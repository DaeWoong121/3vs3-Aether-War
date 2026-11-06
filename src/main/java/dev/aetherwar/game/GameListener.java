package dev.aetherwar.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import dev.aetherwar.item.Items;
import dev.aetherwar.skill.PlayerData;
import dev.aetherwar.skill.SkillItems;
import dev.aetherwar.skill.SkillManager;
import dev.aetherwar.skill.Stat;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GameListener implements Listener {
    private final GameManager game;

    public GameListener(GameManager game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!game.isRunning() || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            RiftNode n = game.nodeAt(b.getLocation());
            if (n != null) {
                e.setCancelled(true);
                game.hitNode(e.getPlayer(), n);
                return;
            }
            TeamColor core = game.coreAt(b.getLocation());
            if (core != null) {
                e.setCancelled(true);
                game.hitCore(e.getPlayer(), core);
            }
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK && game.coreAt(b.getLocation()) != null) {
            e.setCancelled(true); // don't open the beacon UI
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!game.isRunning() || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        if (game.nodeAt(e.getBlock().getLocation()) != null || game.coreAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (game.isRunning() && game.coreAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!game.isRunning()) return;
        Entity d = e.getDamager();
        Entity victimEntity = e.getEntity();
        // golems never hit their own team
        if (d instanceof IronGolem g && game.golemTeam.containsKey(g.getUniqueId())) {
            if (victimEntity instanceof Player vp && game.teamOf(vp) == game.golemTeam.get(g.getUniqueId())) { e.setCancelled(true); return; }
            if (victimEntity instanceof Player vp && (game.phase() == Phase.EXPLORE
                    || (game.phase() == Phase.EXPAND && game.arena().baseAt(vp.getLocation()) != null))) { e.setCancelled(true); return; }
        }
        if (victimEntity instanceof IronGolem g && game.golemTeam.containsKey(g.getUniqueId())) {
            Player a = attackerOf(d);
            if (a != null && game.teamOf(a) == game.golemTeam.get(g.getUniqueId())) { e.setCancelled(true); return; }
        }
        Player attacker = attackerOf(d);
        boolean projectile = d instanceof Projectile;
        SkillManager sk = game.skills;
        if (attacker != null && victimEntity instanceof Player victim && attacker != victim) {
            if (!game.canPvp(attacker, victim)) {
                e.setCancelled(true);
                if (game.teamOf(attacker) != game.teamOf(victim))
                    attacker.sendActionBar(Component.text(pvpReason(), NamedTextColor.GRAY));
                return;
            }
        }
        double mult = 1;
        if (attacker != null && game.teamOf(attacker) != null) {
            if (game.stealthUntil.containsKey(attacker.getUniqueId())) sk.actives.reveal(attacker);
            mult += projectile ? sk.stat(attacker, Stat.BOW_DMG) : sk.stat(attacker, Stat.MELEE_DMG);
            double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (attacker.getHealth() <= maxHp * 0.5) mult += sk.stat(attacker, Stat.LOW_HP_DMG);
            if (victimEntity instanceof LivingEntity le) {
                var vmax = le.getAttribute(Attribute.MAX_HEALTH);
                if (vmax != null && le.getHealth() < vmax.getValue() * 0.3) mult += sk.stat(attacker, Stat.EXECUTE_DMG);
            }
            if (victimEntity instanceof Player victim && sk.actives.isMarkedBy(victim, game.teamOf(attacker)))
                mult += 0.2 + sk.stat(attacker, Stat.MARKED_DMG);
            if (game.isGuardian(victimEntity) || game.isBoss(victimEntity)) mult += sk.stat(attacker, Stat.GUARDIAN_DMG);
            if (d instanceof Arrow && sk.deploy.turretArrows.containsKey(d.getUniqueId())) {
                if (Boolean.TRUE.equals(sk.deploy.turretArrows.remove(d.getUniqueId())))
                    victimEntity.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, victimEntity.getLocation(), 1);
            }
        }
        if (victimEntity instanceof Player victim && game.teamOf(victim) != null) {
            double reduce = sk.stat(victim, Stat.DMG_TAKEN_REDUCE);
            if (game.arena().baseAt(victim.getLocation()) == game.teamOf(victim)) reduce += sk.stat(victim, Stat.BASE_DMG_TAKEN_REDUCE);
            if (Items.countCrystals(victim) > 0) reduce += sk.stat(victim, Stat.CARRY_ARMOR);
            mult *= Math.max(0.3, 1 - reduce);
            mult *= game.baseDamageMultiplier(victim);
        }
        e.setDamage(e.getDamage() * mult);
        if (game.boss != null && game.boss.isMain(victimEntity) && attacker != null) game.boss.onDamaged(attacker, e.getFinalDamage());
    }

    private Player attackerOf(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Phoenix: once per phase, survive a killing blow. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!game.isRunning() || !(e.getEntity() instanceof Player p) || game.teamOf(p) == null) return;
        if (p.getHealth() - e.getFinalDamage() > 0) return;
        PlayerData pd = game.skills.data(p);
        if (game.skills.stat(p, Stat.PHOENIX) <= 0 || pd.phoenixPhase == game.phase().ordinal()) return;
        pd.phoenixPhase = game.phase().ordinal();
        e.setCancelled(true);
        p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), 8));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 2));
        p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.3);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        p.sendActionBar(Component.text("불사조! 이번 페이즈에 한 번 살아났어요", NamedTextColor.GOLD));
    }

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!game.isRunning()) return;
        if (e.getEntity() instanceof IronGolem g && game.golemTeam.containsKey(g.getUniqueId())) {
            if (e.getTarget() instanceof Player t && game.teamOf(t) == game.golemTeam.get(g.getUniqueId())) e.setCancelled(true);
            if (e.getTarget() instanceof IronGolem other && game.golemTeam.get(other.getUniqueId()) == game.golemTeam.get(g.getUniqueId())) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!game.isRunning()) return;
        var th = game.skills.actives.thrown.remove(e.getEntity().getUniqueId());
        if (th != null) {
            game.skills.actives.impact(th, e.getEntity().getLocation());
            return;
        }
        if (e.getEntity() instanceof Arrow a && Boolean.TRUE.equals(game.skills.deploy.turretArrows.get(a.getUniqueId())) && e.getHitBlock() != null) {
            game.skills.deploy.turretArrows.remove(a.getUniqueId());
            a.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, a.getLocation(), 1);
            Player shooter = a.getShooter() instanceof Player sp ? sp : null;
            if (shooter != null) for (LivingEntity le : game.skills.actives.hostilesNear(shooter, a.getLocation(), 2.5)) le.damage(3, shooter);
            a.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!game.isRunning()) return;
        Location to = e.getTo(), from = e.getFrom();
        Player p = e.getPlayer();
        if (to.getBlockX() != from.getBlockX() || to.getBlockY() != from.getBlockY() || to.getBlockZ() != from.getBlockZ())
            game.skills.deploy.stepped(p, to.getBlock());
        Long dive = game.skills.actives.diving.get(p.getUniqueId());
        if (dive != null) {
            if (System.currentTimeMillis() > dive) game.skills.actives.diving.remove(p.getUniqueId());
            else if (((org.bukkit.entity.Entity) p).isOnGround() && System.currentTimeMillis() > dive - 5200) {
                game.skills.actives.diving.remove(p.getUniqueId());
                diveLanding(p);
            }
        }
    }

    /** Team combo: a jump pad launch followed by a dash owner landing. */
    private void diveLanding(Player p) {
        TeamColor t = game.teamOf(p);
        int hit = 0;
        for (LivingEntity le : game.skills.actives.hostilesNear(p, p.getLocation(), 4)) {
            le.damage(6, p);
            le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.9).setY(0.5));
            hit++;
        }
        p.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, p.getLocation(), 3, 1, 0.2, 1, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
        if (hit > 0) game.skills.actives.combo(t, "공중 강습", "점프대로 날아올라 착지하며 주변 적에게 피해");
    }

    // ------------------------------------------------------------ skill items

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String tag = SkillItems.tag(e.getItem());
        if (tag == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (tag.equals("menu")) dev.aetherwar.gui.SkillMenu.open(game, p);
        else if (tag.startsWith("active:")) game.skills.actives.cast(p, Integer.parseInt(tag.substring(7)));
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (SkillItems.tag(e.getMainHandItem()) != null || SkillItems.tag(e.getOffHandItem()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (SkillItems.tag(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof dev.aetherwar.gui.Menu) return;
        if (SkillItems.tag(e.getCurrentItem()) != null || SkillItems.tag(e.getCursor()) != null) { e.setCancelled(true); return; }
        if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p
                && SkillItems.tag(p.getInventory().getItem(e.getHotbarButton())) != null) e.setCancelled(true);
    }

    private String pvpReason() {
        return switch (game.phase()) {
            case EXPLORE -> "1페이즈에는 PvP가 꺼져 있어요.";
            case EXPAND -> "2페이즈에는 중립 지역에서만 싸울 수 있어요.";
            case CLASH -> "";
        };
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (!game.isRunning() || game.teamOf(e.getPlayer()) == null) return;
        Player p = e.getPlayer();
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        int dropped = game.dropHalfCrystals(p);
        if (dropped > 0) p.sendMessage(Component.text("결정 " + dropped + "개를 떨궜어요.", NamedTextColor.RED));
        Player killer = p.getKiller();
        if (killer != null && game.teamOf(killer) != null && game.teamOf(killer) != game.teamOf(p)) {
            SkillManager sk = game.skills;
            game.addContribution(killer, 15 + (int) sk.stat(killer, Stat.KILL_CONTRIB));
            double heal = sk.stat(killer, Stat.KILL_HEAL);
            if (heal > 0) SkillManager.heal(killer, heal);
            int bonus = (int) sk.stat(killer, Stat.KILL_CRYSTALS);
            if (bonus > 0) killer.getInventory().addItem(Items.crystal(bonus));
        }
        game.skills.actives.reveal(p);
        game.scheduleRespawn(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!game.isRunning() || game.teamOf(e.getPlayer()) == null) return;
        var s = game.spawnOf(e.getPlayer());
        if (s != null) e.setRespawnLocation(s);
        e.getPlayer().setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!game.isRunning()) return;
        if (game.isBoss(e.getEntity()) || game.golemTeam.containsKey(e.getEntity().getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            game.golemTeam.remove(e.getEntity().getUniqueId());
            if (game.boss.isMain(e.getEntity())) game.boss.died(e.getEntity().getKiller());
            return;
        }
        if (!game.isGuardian(e.getEntity())) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        game.guardianDied(e.getEntity(), e.getEntity().getKiller());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        game.hud().show(e.getPlayer());
        if (game.boss != null) game.boss.showTo(e.getPlayer());
        if (!game.isRunning()) return;
        TeamColor t = game.teamOf(e.getPlayer());
        if (t == null) {
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            e.getPlayer().sendMessage(Component.text("경기가 진행 중이라 관전 모드로 들어왔어요.", NamedTextColor.GRAY));
        }
    }

}
