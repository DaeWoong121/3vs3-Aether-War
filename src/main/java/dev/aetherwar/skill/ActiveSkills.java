package dev.aetherwar.skill;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.RiftNode;
import dev.aetherwar.game.TeamColor;
import dev.aetherwar.item.Items;
import dev.aetherwar.item.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Casting active skills. */
public class ActiveSkills {
    private final SkillManager skills;
    private final GameManager game;
    /** Thrown potion snowballs -> what they do on impact. */
    public final Map<UUID, Thrown> thrown = new HashMap<>();
    public final Set<UUID> golems = new HashSet<>();
    /** Players launched by a jump pad who can land a dive (team combo) until this time. */
    public final Map<UUID, Long> diving = new HashMap<>();
    /** Enemies marked by a scan: uuid -> (marking team, until ms). */
    public final Map<UUID, Mark> marks = new HashMap<>();

    public record Thrown(UUID caster, Skill skill) {}
    public record Mark(TeamColor team, long until) {}

    public ActiveSkills(SkillManager skills) {
        this.skills = skills;
        this.game = skills.game();
    }

    public void cast(Player p, int slot) {
        if (!game.isRunning() || p.getGameMode() == GameMode.SPECTATOR) return;
        PlayerData d = skills.data(p);
        String id = d.loadout[slot];
        if (id == null) {
            p.sendActionBar(Component.text("빈 칸이에요. 스킬트리(나침반)에서 액티브 스킬을 배우세요.", NamedTextColor.GRAY));
            return;
        }
        Skill s = SkillRegistry.get(id);
        long left = skills.cooldownLeftMs(p, s);
        if (left > 0) {
            p.sendActionBar(Component.text(s.name + " 재사용까지 " + (left / 1000 + 1) + "초", NamedTextColor.RED));
            return;
        }
        boolean ok = run(p, s);
        if (ok) {
            skills.startCooldown(p, s, slot);
            p.sendActionBar(Component.text("✦ " + s.name, NamedTextColor.LIGHT_PURPLE));
        }
    }

    private TeamColor team(Player p) { return game.teamOf(p); }

    /** Enemy players within radius that this player may hurt right now. */
    public List<Player> enemiesNear(Player p, Location c, double r) {
        List<Player> out = new ArrayList<>();
        TeamColor t = team(p);
        if (t == null) return out;
        for (Player e : game.members(t.other())) {
            if (e.getGameMode() == GameMode.SPECTATOR || e.getWorld() != c.getWorld()) continue;
            if (e.getLocation().distanceSquared(c) <= r * r && game.canPvp(p, e)) out.add(e);
        }
        return out;
    }

    public List<LivingEntity> hostilesNear(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>(enemiesNear(p, c, r));
        for (Entity e : c.getWorld().getNearbyEntities(c, r, r, r))
            if (e instanceof LivingEntity le && (game.isGuardian(e) || game.isBoss(e))) out.add(le);
        return out;
    }

    public List<Player> alliesNear(Player p, double r) {
        List<Player> out = new ArrayList<>();
        TeamColor t = team(p);
        if (t == null) return out;
        for (Player a : game.members(t)) if (a.getGameMode() != GameMode.SPECTATOR && SkillManager.near(a, p, r)) out.add(a);
        return out;
    }

    private double potionRadius(Player p, double r) { return r * (1 + skills.stat(p, Stat.POTION_RADIUS_MULT)); }
    private int potionTicks(Player p, int seconds) { return (int) (seconds * 20 * (1 + skills.stat(p, Stat.POTION_DURATION_MULT))); }

    private Location lookTarget(Player p, double max) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), max);
        if (r != null && r.getHitBlock() != null) return r.getHitBlock().getLocation().add(0.5, 1, 0.5);
        Location l = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(max));
        return l.getWorld().getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
    }

    private boolean run(Player p, Skill s) {
        TeamColor t = team(p);
        Location loc = p.getLocation();
        switch (s.active) {
            case DASH -> {
                Vector v = loc.getDirection().setY(0).normalize().multiply(s.power).setY(0.35);
                p.setVelocity(v);
                p.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.3, 0.2, 0.3, 0.05);
                p.getWorld().playSound(loc, Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
            }
            case SHIELD_DASH -> {
                p.setVelocity(loc.getDirection().setY(0).normalize().multiply(1.6).setY(0.3));
                p.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
                Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> {
                    for (LivingEntity e : hostilesNear(p, p.getLocation(), s.radius)) {
                        e.damage(s.power, p);
                        e.setVelocity(e.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.2).setY(0.4));
                    }
                }, 6L);
            }
            case HOOK -> {
                RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), loc.getDirection(), s.radius, 0.6,
                        e -> e instanceof Player o && game.teamOf(o) == (t == null ? null : t.other()) && game.canPvp(p, o));
                if (r == null || !(r.getHitEntity() instanceof Player target)) { p.sendActionBar(Component.text("대상이 없어요.", NamedTextColor.GRAY)); return false; }
                Vector pull = loc.toVector().subtract(target.getLocation().toVector()).multiply(0.22).setY(0.5);
                target.setVelocity(pull);
                p.getWorld().playSound(loc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
                line(p.getEyeLocation(), target.getEyeLocation(), Particle.CRIT);
            }
            case GROUND_SLAM -> {
                for (LivingEntity e : hostilesNear(p, loc, s.radius)) {
                    e.damage(s.power, p);
                    e.setVelocity(new Vector(0, 0.9, 0));
                }
                ring(loc, s.radius, Particle.EXPLOSION, 12);
                p.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
            }
            case WAR_CRY -> {
                for (Player a : alliesNear(p, s.radius)) {
                    SkillManager.potion(a, PotionEffectType.STRENGTH, 0, s.duration * 20);
                    SkillManager.potion(a, PotionEffectType.SPEED, 0, s.duration * 20);
                }
                p.getWorld().playSound(loc, Sound.EVENT_RAID_HORN, 1f, 1.2f);
                ring(loc, s.radius, Particle.FLAME, 30);
            }
            case TAUNT -> {
                for (Player e : enemiesNear(p, loc, s.radius)) {
                    SkillManager.potion(e, PotionEffectType.GLOWING, 0, s.duration * 20);
                    SkillManager.potion(e, PotionEffectType.WEAKNESS, 0, s.duration * 20);
                }
                p.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.2f);
            }
            case WARD -> {
                for (Player a : alliesNear(p, s.radius)) SkillManager.potion(a, PotionEffectType.RESISTANCE, 1, s.duration * 20);
                ring(loc, s.radius, Particle.END_ROD, 30);
                p.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            }
            case GOLEM -> {
                IronGolem g = p.getWorld().spawn(loc.clone().add(loc.getDirection().setY(0).normalize().multiply(2)), IronGolem.class, e -> {
                    e.setPlayerCreated(true);
                    e.customName(Component.text(t.displayName + " 골렘", t.textColor));
                    e.setCustomNameVisible(true);
                    e.setPersistent(false);
                });
                golems.add(g.getUniqueId());
                game.golemTeam.put(g.getUniqueId(), t);
                Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> { g.remove(); golems.remove(g.getUniqueId()); }, s.duration * 20L);
            }
            case STEALTH -> {
                for (Player e : game.members(t.other())) e.hidePlayer(skills.plugin(), p);
                SkillManager.potion(p, PotionEffectType.INVISIBILITY, 0, s.duration * 20);
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 20, 0.3, 0.8, 0.3, 0.02);
                game.stealthUntil.put(p.getUniqueId(), System.currentTimeMillis() + s.duration * 1000L);
                Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> reveal(p), s.duration * 20L);
            }
            case SCAN, HUNT -> {
                double r = s.radius * (1 + skills.stat(p, Stat.SCAN_RANGE_MULT));
                int n = 0;
                for (Player e : game.members(t.other())) {
                    if (e.getGameMode() == GameMode.SPECTATOR || !SkillManager.near(e, p, r)) continue;
                    if (skills.stat(e, Stat.SMUGGLER) > 0 && Items.countCrystals(e) > 0) continue;
                    SkillManager.potion(e, PotionEffectType.GLOWING, 0, s.duration * 20);
                    // team combo: a scan while our team has a hunter warrior marks the target for bonus damage
                    if (skills.teamHasBranch(t, PlayerClass.WARRIOR, 2) || s.active == Active.HUNT) {
                        marks.put(e.getUniqueId(), new Mark(t, System.currentTimeMillis() + s.duration * 1000L));
                    }
                    n++;
                }
                if (s.active == Active.HUNT) for (Player a : alliesNear(p, 30)) SkillManager.potion(a, PotionEffectType.SPEED, 0, s.duration * 20);
                p.sendActionBar(Component.text("적 " + n + "명 발견", NamedTextColor.AQUA));
                p.getWorld().playSound(loc, Sound.BLOCK_BELL_RESONATE, 1f, 1.5f);
                if (n > 0 && skills.teamHasBranch(t, PlayerClass.WARRIOR, 2) && classOf(p) == PlayerClass.MINER)
                    combo(t, "표식 사냥", "표식된 적이 우리 팀에게 받는 피해 +20%");
            }
            case RECALL -> {
                p.sendActionBar(Component.text(s.duration + "초 뒤 귀환", NamedTextColor.AQUA));
                Location start = loc.clone();
                Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> {
                    if (!game.isRunning() || p.getGameMode() == GameMode.SPECTATOR) return;
                    Location sp = game.spawnOf(p);
                    if (sp != null) { p.teleport(sp); p.getWorld().playSound(sp, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f); }
                }, s.duration * 20L);
                p.getWorld().spawnParticle(Particle.PORTAL, start, 60, 0.4, 1, 0.4, 0.5);
            }
            case NODE_RESONANCE -> {
                int n = game.refreshNodesNear(loc, s.radius);
                if (n == 0) { p.sendActionBar(Component.text("근처에 재생할 노드가 없어요.", NamedTextColor.GRAY)); return false; }
                p.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1f);
            }
            case EARTH_SHATTER -> {
                int n = game.harvestNodesNear(p, loc, s.radius);
                if (n == 0) { p.sendActionBar(Component.text("근처에 캘 노드가 없어요.", NamedTextColor.GRAY)); return false; }
                ring(loc, s.radius, Particle.BLOCK_CRUMBLE, 40);
            }
            case CRYSTAL_PORTAL -> {
                if (Items.countCrystals(p) + Items.countRefined(p) == 0) { p.sendActionBar(Component.text("들고 있는 결정이 없어요.", NamedTextColor.GRAY)); return false; }
                game.deposit(p);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.add(0, 1, 0), 60, 0.3, 0.6, 0.3, 0.1);
            }
            case VEIN_BLAST -> {
                Block b = p.getTargetBlockExact((int) s.radius);
                RiftNode n = b == null ? null : game.nodeAt(b.getLocation());
                if (n == null || !game.blastNode(p, n, (int) s.power, s.duration)) { p.sendActionBar(Component.text("캘 수 있는 노드를 바라보세요.", NamedTextColor.GRAY)); return false; }
            }
            case REFINE -> { return refine(p, false, false); }
            case MASS_REFINE -> { return refine(p, true, false); }
            case TRANSMUTE -> { return refine(p, true, true); }
            case EXPLOSIVE_CRYSTAL -> {
                if (Items.countCrystals(p) < 3) { p.sendActionBar(Component.text("결정 3개가 필요해요.", NamedTextColor.GRAY)); return false; }
                Items.takeCrystals(p, 3);
                throwPotion(p, s, Color.fromRGB(0xB98CFF));
            }
            case HEAL_TARGET -> {
                RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), loc.getDirection(), s.radius, 0.8,
                        e -> e instanceof Player o && o != p && game.teamOf(o) == t);
                Player target = r != null && r.getHitEntity() instanceof Player o ? o : p;
                SkillManager.heal(target, s.power);
                if (target != p) SkillManager.heal(p, s.power * skills.stat(p, Stat.HEAL_LINK));
                target.getWorld().spawnParticle(Particle.HEART, target.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4);
                p.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            }
            case REGEN_BURST -> {
                for (Player a : alliesNear(p, s.radius)) SkillManager.potion(a, PotionEffectType.REGENERATION, 1, s.duration * 20);
                ring(loc, s.radius, Particle.HEART, 16);
            }
            case ABSORB_BURST -> {
                for (Player a : alliesNear(p, s.radius)) SkillManager.potion(a, PotionEffectType.ABSORPTION, 1, s.duration * 20);
                ring(loc, s.radius, Particle.HAPPY_VILLAGER, 24);
            }
            case TEAM_HEAL -> {
                for (Player a : game.members(t)) if (a.getGameMode() != GameMode.SPECTATOR) {
                    a.setHealth(a.getAttribute(Attribute.MAX_HEALTH).getValue());
                    a.getWorld().spawnParticle(Particle.HEART, a.getLocation().add(0, 2, 0), 8, 0.4, 0.4, 0.4);
                    Sfx.play(a, "capture", 0.6f, 1.4f);
                }
            }
            case POTION_SLOW, POTION_POISON, POTION_WEAKNESS, SMOKE, LEVITATE ->
                    throwPotion(p, s, switch (s.active) {
                        case POTION_SLOW -> Color.fromRGB(0x5A6C81);
                        case POTION_POISON -> Color.fromRGB(0x4E9331);
                        case POTION_WEAKNESS -> Color.fromRGB(0x484D48);
                        case SMOKE -> Color.fromRGB(0x1F1F23);
                        default -> Color.fromRGB(0xCEFFFF);
                    });
            case CHAOS_CLOUD -> skills.deploy.cloud(p, lookTarget(p, 20), potionRadius(p, s.radius), s.duration);
            case TIME_WARP -> {
                for (Player e : enemiesNear(p, loc, potionRadius(p, s.radius))) SkillManager.potion(e, PotionEffectType.SLOWNESS, 3, potionTicks(p, s.duration));
                ring(loc, s.radius, Particle.REVERSE_PORTAL, 40);
                p.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
            }
            case WALL, BARRICADE -> {
                boolean big = skills.stat(p, Stat.WALL_BIG) > 0 && s.active == Active.WALL && classOf(p) == PlayerClass.BUILDER;
                int w = big ? 5 : (int) s.power, h = big ? 3 : 2;
                Material m = s.active == Active.BARRICADE ? Material.TINTED_GLASS : (classOf(p) == PlayerClass.WARRIOR ? Material.IRON_BARS : Material.COBBLESTONE_WALL);
                if (s.active == Active.WALL && classOf(p) == PlayerClass.BUILDER) m = Material.STONE_BRICKS;
                int dur = big ? s.duration * 2 : s.duration;
                if (!skills.deploy.wall(p, w, h, m, dur)) return false;
                if (skills.stat(p, Stat.WALL_TURRET) > 0) skills.deploy.turret(p, p.getLocation().add(loc.getDirection().setY(0).normalize().multiply(-1)), 3, 12, 10, false);
            }
            case CITADEL -> skills.deploy.ring(p, (int) s.power, s.duration);
            case CORE_WARD -> game.shieldCore(t, s.duration);
            case TURRET -> { return skills.deploy.turret(p, loc.clone().add(loc.getDirection().setY(0).normalize().multiply(1.5)), s.power, s.radius, s.duration, false); }
            case BIG_TURRET -> { return skills.deploy.turret(p, loc.clone().add(loc.getDirection().setY(0).normalize().multiply(1.5)), s.power, s.radius, s.duration, true); }
            case TURRET_SWARM -> {
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * 2 * i / 3;
                    skills.deploy.turret(p, loc.clone().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5), s.power, s.radius, s.duration, false, true);
                }
            }
            case TURRET_LAUNCH -> {
                Location target = lookTarget(p, 20);
                return skills.deploy.turret(p, target, s.power, s.radius, s.duration, false);
            }
            case TRAP -> { return skills.deploy.trap(p, false, s.power, s.duration); }
            case MINE -> { return skills.deploy.trap(p, true, s.power, s.duration); }
            case JUMP_PAD -> { return skills.deploy.pad(p, s.power, (int) (s.duration * (1 + skills.stat(p, Stat.PAD_HEIGHT) * 0))); }
            case BRIDGE -> { return skills.deploy.bridge(p, (int) s.power, s.duration); }
            case FORWARD_SPAWN -> {
                if (game.arena().baseAt(loc) != null) { p.sendActionBar(Component.text("기지 밖에서 써야 해요.", NamedTextColor.GRAY)); return false; }
                game.setForwardSpawn(t, loc.clone(), s.duration);
                skills.deploy.flag(p, loc.clone(), s.duration);
            }
            case RALLY -> {
                for (Player a : game.members(t)) if (a != p) a.sendActionBar(Component.text(p.getName() + "에게 " + s.duration + "초 뒤 소환돼요!", NamedTextColor.GOLD));
                Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> {
                    if (!game.isRunning()) return;
                    for (Player a : game.members(t)) if (a != p && a.getGameMode() != GameMode.SPECTATOR) a.teleport(p.getLocation());
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                }, s.duration * 20L);
            }
            default -> { return false; }
        }
        return true;
    }

    private PlayerClass classOf(Player p) { return skills.classOf(p); }

    public void reveal(Player p) {
        game.stealthUntil.remove(p.getUniqueId());
        for (Player e : Bukkit.getOnlinePlayers()) e.showPlayer(skills.plugin(), p);
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private boolean refine(Player p, boolean all, boolean oneToOne) {
        int have = Items.countCrystals(p);
        int per = oneToOne ? 1 : 2;
        if (have < per) { p.sendActionBar(Component.text("결정이 " + per + "개 필요해요.", NamedTextColor.GRAY)); return false; }
        int made = all ? have / per : 1;
        Items.takeCrystals(p, made * per);
        int refund = 0;
        for (int i = 0; i < made; i++) if (Math.random() < skills.stat(p, Stat.REFINE_REFUND)) refund++;
        p.getInventory().addItem(Items.refined(made));
        if (refund > 0) p.getInventory().addItem(Items.crystal(refund));
        double heal = skills.stat(p, Stat.REFINE_HEAL);
        if (heal > 0) for (Player a : alliesNear(p, 6)) SkillManager.heal(a, heal);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.3f);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.2, 0), 40, 0.4, 0.4, 0.4, 0.8);
        p.sendActionBar(Component.text("정제 결정 +" + made + (refund > 0 ? " (결정 " + refund + "개 반환)" : ""), NamedTextColor.AQUA));
        game.addContribution(p, 4 * made);
        return true;
    }

    private void throwPotion(Player p, Skill s, Color color) {
        Snowball ball = p.launchProjectile(Snowball.class, p.getLocation().getDirection().multiply(1.3));
        ItemStack look = new ItemStack(s.active == Active.EXPLOSIVE_CRYSTAL ? Material.AMETHYST_SHARD : Material.SPLASH_POTION);
        if (look.getItemMeta() instanceof PotionMeta pm) { pm.setColor(color); look.setItemMeta(pm); }
        ball.setItem(look);
        thrown.put(ball.getUniqueId(), new Thrown(p.getUniqueId(), s));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 1f, 1f);
    }

    /** A thrown potion landed. */
    public void impact(Thrown th, Location at) {
        Player p = Bukkit.getPlayer(th.caster());
        if (p == null || !game.isRunning()) return;
        Skill s = th.skill();
        double r = s.active == Active.EXPLOSIVE_CRYSTAL ? s.radius : potionRadius(p, s.radius);
        int ticks = potionTicks(p, s.duration);
        double extra = skills.stat(p, Stat.POTION_EXTRA_DMG);
        int hits = 0;
        for (LivingEntity e : hostilesNear(p, at, r)) {
            hits++;
            switch (s.active) {
                case POTION_SLOW -> e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.SLOWNESS, ticks, 1));
                case POTION_POISON -> e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.POISON, ticks, 0));
                case POTION_WEAKNESS -> e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.WEAKNESS, ticks, 0));
                case SMOKE -> e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.BLINDNESS, ticks, 0));
                case LEVITATE -> e.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.LEVITATION, ticks, 0));
                case EXPLOSIVE_CRYSTAL -> e.damage(s.power, p);
                default -> { }
            }
            if (extra > 0 && s.active != Active.EXPLOSIVE_CRYSTAL) e.damage(extra, p);
        }
        double steal = skills.stat(p, Stat.POTION_LIFESTEAL);
        if (steal > 0 && hits > 0) for (Player a : alliesNear(p, 8)) SkillManager.heal(a, steal * hits);
        Particle part = s.active == Active.SMOKE ? Particle.CAMPFIRE_SIGNAL_SMOKE : (s.active == Active.EXPLOSIVE_CRYSTAL ? Particle.EXPLOSION : Particle.WITCH);
        at.getWorld().spawnParticle(part, at, s.active == Active.SMOKE ? 80 : 40, r / 2, 0.6, r / 2, 0.02);
        at.getWorld().playSound(at, s.active == Active.EXPLOSIVE_CRYSTAL ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 1f);
    }

    public void combo(TeamColor t, String name, String desc) {
        for (Player a : game.members(t)) {
            a.sendMessage(Component.text("✦ 팀 콤보: " + name, NamedTextColor.GOLD).append(Component.text("  " + desc, NamedTextColor.GRAY)));
            Sfx.play(a, "capture", 0.7f, 1.6f);
        }
    }

    public static void ring(Location c, double r, Particle part, int n) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            c.getWorld().spawnParticle(part, c.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0, 0, 0);
        }
    }

    public static void line(Location a, Location b, Particle part) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        d.normalize();
        for (double i = 0; i < len; i += 0.5) a.getWorld().spawnParticle(part, a.clone().add(d.clone().multiply(i)), 1, 0, 0, 0, 0);
    }

    public boolean isMarkedBy(Player victim, TeamColor team) {
        Mark m = marks.get(victim.getUniqueId());
        return m != null && m.team() == team && m.until() > System.currentTimeMillis();
    }
}
