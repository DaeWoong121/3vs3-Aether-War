package dev.aetherwar.skill;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Temporary things players build: walls, turrets, traps, jump pads, bridges, clouds, flags. */
public class Deployables {
    private final SkillManager skills;
    private final GameManager game;

    /** Every temporary block we placed -> original block data, so we can always restore. */
    private final Map<Location, BlockData> placed = new HashMap<>();
    public final List<Trap> traps = new ArrayList<>();
    public final List<Pad> pads = new ArrayList<>();
    private final List<Turret> turrets = new ArrayList<>();
    /** Arrows fired by turrets: arrow uuid -> explosive? */
    public final Map<UUID, Boolean> turretArrows = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();

    public record Trap(UUID owner, TeamColor team, Location block, boolean mine, double power, long until) {}
    public record Pad(UUID owner, TeamColor team, Location block, double power, long until) {}
    private record Turret(UUID owner, Location block, long until) {}

    public Deployables(SkillManager skills) {
        this.skills = skills;
        this.game = skills.game();
    }

    // ------------------------------------------------------------ temp blocks

    private boolean placeTemp(Block b, Material m) {
        if (!b.getType().isAir() && b.getType() != Material.SHORT_GRASS && b.getType() != Material.MOSS_CARPET && !b.getType().name().endsWith("_FLOWER")
                && b.getType() != Material.POPPY && b.getType() != Material.CORNFLOWER) return false;
        if (game.nodeAt(b.getLocation()) != null || game.coreAt(b.getLocation()) != null) return false;
        placed.putIfAbsent(b.getLocation(), b.getBlockData().clone());
        b.setType(m, false);
        return true;
    }

    private boolean replaceTemp(Block b, Material m) {
        if (game.nodeAt(b.getLocation()) != null || game.coreAt(b.getLocation()) != null) return false;
        placed.putIfAbsent(b.getLocation(), b.getBlockData().clone());
        b.setType(m, false);
        return true;
    }

    private void restore(Location l) {
        BlockData d = placed.remove(l);
        if (d != null) l.getBlock().setBlockData(d, false);
    }

    private void restoreLater(List<Location> locs, int seconds) {
        tasks.add(Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> {
            for (Location l : locs) {
                restore(l);
                l.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, l.clone().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3, 0, Material.STONE.createBlockData());
            }
        }, seconds * 20L));
    }

    public boolean isTemp(Location l) {
        return placed.containsKey(l);
    }

    public void clearAll() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        for (Location l : new ArrayList<>(placed.keySet())) restore(l);
        traps.clear();
        pads.clear();
        turrets.clear();
        turretArrows.clear();
    }

    // ------------------------------------------------------------ walls

    public boolean wall(Player p, int width, int height, Material m, int seconds) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location base = p.getLocation().add(dir.clone().multiply(2));
        List<Location> locs = new ArrayList<>();
        for (int w = -width / 2; w <= width / 2; w++) {
            Location col = base.clone().add(side.clone().multiply(w));
            Block ground = col.getWorld().getHighestBlockAt(col.getBlockX(), col.getBlockZ());
            if (ground.getY() > p.getLocation().getBlockY() + 2) ground = col.getBlock().getRelative(BlockFace.DOWN);
            for (int h = 1; h <= height; h++) {
                Block b = ground.getRelative(0, h, 0);
                if (placeTemp(b, m)) locs.add(b.getLocation());
            }
        }
        if (locs.isEmpty()) return false;
        p.getWorld().playSound(base, Sound.BLOCK_STONE_PLACE, 1f, 0.8f);
        restoreLater(locs, seconds);
        return true;
    }

    public void ring(Player p, int radius, int seconds) {
        List<Location> locs = new ArrayList<>();
        Location c = p.getLocation();
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48;
            Block col = c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius).getBlock();
            for (int h = 0; h < 3; h++) if (placeTemp(col.getRelative(0, h, 0), Material.STONE_BRICKS)) locs.add(col.getRelative(0, h, 0).getLocation());
        }
        p.getWorld().playSound(c, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
        restoreLater(locs, seconds);
    }

    public boolean bridge(Player p, int length, int seconds) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Location start = p.getLocation().subtract(0, 1, 0);
        List<Location> locs = new ArrayList<>();
        for (int i = 1; i <= length; i++) {
            Block b = start.clone().add(dir.clone().multiply(i)).getBlock();
            if (placeTemp(b, Material.SPRUCE_PLANKS)) locs.add(b.getLocation());
        }
        if (locs.isEmpty()) return false;
        p.getWorld().playSound(start, Sound.BLOCK_WOOD_PLACE, 1f, 1f);
        restoreLater(locs, seconds);
        return true;
    }

    // ------------------------------------------------------------ turrets

    public boolean turret(Player p, Location at, double damage, double range, int seconds, boolean explosive) {
        return turret(p, at, damage, range, seconds, explosive, false);
    }

    public boolean turret(Player p, Location at, double damage, double range, int seconds, boolean explosive, boolean ignoreLimit) {
        UUID owner = p.getUniqueId();
        turrets.removeIf(t -> t.until < System.currentTimeMillis());
        int max = 1 + (int) skills.stat(p, Stat.TURRET_MAX);
        if (!ignoreLimit && turrets.stream().filter(t -> t.owner.equals(owner)).count() >= max) {
            p.sendActionBar(Component.text("포탑은 " + max + "개까지 세울 수 있어요.", NamedTextColor.GRAY));
            return false;
        }
        Block ground = at.getWorld().getHighestBlockAt(at.getBlockX(), at.getBlockZ());
        Block b = ground.getRelative(BlockFace.UP);
        if (b.getY() > at.getBlockY() + 3) b = at.getBlock();
        if (!placeTemp(b, explosive ? Material.CRAFTER : Material.DISPENSER)) { p.sendActionBar(Component.text("여기는 세울 수 없어요.", NamedTextColor.GRAY)); return false; }
        TeamColor team = game.teamOf(p);
        double dmg = damage * (1 + skills.stat(p, Stat.TURRET_DMG));
        int dur = (int) (seconds * (1 + skills.stat(p, Stat.TURRET_DURATION)));
        long period = skills.stat(p, Stat.TURRET_RATE) > 0 ? 10L : 20L;
        boolean hitsGuardians = skills.stat(p, Stat.TURRET_GUARDIAN) > 0;
        Location muzzle = b.getLocation().add(0.5, 1.3, 0.5);
        long until = System.currentTimeMillis() + dur * 1000L;
        turrets.add(new Turret(owner, b.getLocation(), until));
        TextDisplay label = muzzle.getWorld().spawn(muzzle.clone().add(0, 0.6, 0), TextDisplay.class, d -> {
            d.text(Component.text(team.displayName + " 포탑", team.textColor));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
        });
        Block placedBlock = b;
        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                Player shooter = Bukkit.getPlayer(owner);
                if (System.currentTimeMillis() > until || !game.isRunning() || shooter == null) {
                    restore(placedBlock.getLocation());
                    label.remove();
                    cancel();
                    return;
                }
                LivingEntity target = null;
                double best = range * range;
                for (Player e : game.members(team.other())) {
                    if (e.getGameMode() == GameMode.SPECTATOR || e.getWorld() != muzzle.getWorld() || !game.canPvp(shooter, e)) continue;
                    double d = e.getLocation().distanceSquared(muzzle);
                    if (d < best) { best = d; target = e; }
                }
                if (target == null) {
                    for (LivingEntity e : muzzle.getWorld().getNearbyLivingEntities(muzzle, range)) {
                        if (!(game.isGuardian(e) || game.isBoss(e))) continue;
                        if (!hitsGuardians && !game.isBoss(e)) continue;
                        target = e;
                        break;
                    }
                }
                if (target == null) return;
                Vector v = target.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(muzzle.toVector());
                Arrow arrow = muzzle.getWorld().spawnArrow(muzzle, v.normalize(), 2.2f, 2f);
                arrow.setShooter(shooter);
                arrow.setDamage(dmg / 2.2 * (hitsGuardians && game.isGuardian(target) ? 2 : 1));
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                turretArrows.put(arrow.getUniqueId(), explosive);
                muzzle.getWorld().playSound(muzzle, Sound.BLOCK_DISPENSER_LAUNCH, 0.8f, 1.2f);
            }
        }.runTaskTimer(skills.plugin(), 10L, period);
        tasks.add(task);
        p.getWorld().playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.4f);
        return true;
    }

    // ------------------------------------------------------------ traps & pads

    public boolean trap(Player p, boolean mine, double power, int seconds) {
        UUID owner = p.getUniqueId();
        traps.removeIf(t -> t.until < System.currentTimeMillis());
        if (!mine && traps.stream().filter(t -> t.owner.equals(owner) && !t.mine).count() >= 3) {
            p.sendActionBar(Component.text("함정은 3개까지예요.", NamedTextColor.GRAY));
            return false;
        }
        Block b = p.getLocation().getBlock();
        if (!placeTemp(b, mine ? Material.POLISHED_BLACKSTONE_PRESSURE_PLATE : Material.STONE_PRESSURE_PLATE)) return false;
        Trap t = new Trap(owner, game.teamOf(p), b.getLocation(), mine, power, System.currentTimeMillis() + seconds * 1000L);
        traps.add(t);
        p.playSound(p.getLocation(), Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 1f);
        tasks.add(Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> { traps.remove(t); restore(t.block); }, seconds * 20L));
        return true;
    }

    /** Called when a player moves onto a block. */
    public void stepped(Player p, Block b) {
        TeamColor team = game.teamOf(p);
        if (team == null) return;
        Iterator<Trap> it = traps.iterator();
        while (it.hasNext()) {
            Trap t = it.next();
            if (!t.block.equals(b.getLocation()) || t.team == team) continue;
            Player owner = Bukkit.getPlayer(t.owner);
            if (owner == null || !game.canPvp(owner, p)) continue;
            it.remove();
            restore(t.block);
            if (skills.stat(p, Stat.TRAP_IMMUNE) > 0) { p.sendActionBar(Component.text("함정을 피했어요.", NamedTextColor.AQUA)); return; }
            if (t.mine) {
                p.damage(t.power, owner);
                p.getWorld().spawnParticle(Particle.EXPLOSION, b.getLocation().add(0.5, 0.5, 0.5), 3);
                p.getWorld().playSound(b.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            } else {
                p.damage(t.power, owner);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                p.getWorld().playSound(b.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
                // team combo: trap + alchemist poison = toxic trap
                if (skills.teamHas(t.team, "a_poison")) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                    p.getWorld().spawnParticle(Particle.WITCH, p.getLocation(), 30, 0.6, 0.6, 0.6, 0.02);
                    skills.actives.combo(t.team, "독안개 함정", "함정에 걸린 적이 중독돼요");
                }
            }
            return;
        }
        for (Pad pad : pads) {
            if (!pad.block.equals(b.getRelative(BlockFace.DOWN).getLocation()) && !pad.block.equals(b.getLocation())) continue;
            if (pad.team != team || pad.until < System.currentTimeMillis()) continue;
            Player owner = Bukkit.getPlayer(pad.owner);
            double height = 1 + (owner == null ? 0 : skills.stat(owner, Stat.PAD_HEIGHT));
            Vector v = p.getLocation().getDirection().setY(0).normalize().multiply(1.2).setY(pad.power * height);
            p.setVelocity(v);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 1f, 0.7f);
            p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation(), 15, 0.4, 0.1, 0.4);
            if (owner != null && skills.stat(owner, Stat.PAD_SPEED) > 0) SkillManager.potion(p, PotionEffectType.SPEED, 1, 120);
            if (owner != null && skills.stat(owner, Stat.PAD_ABSORB) > 0) SkillManager.potion(p, PotionEffectType.ABSORPTION, 0, 160);
            if (skills.data(p).owns("w_dash") || skills.data(p).owns("w_x_shielddash")) skills.actives.diving.put(p.getUniqueId(), System.currentTimeMillis() + 6000);
            return;
        }
    }

    public boolean pad(Player p, double power, int seconds) {
        Block b = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (b.getType().isAir()) return false;
        if (!replaceTemp(b, Material.SLIME_BLOCK)) return false;
        Pad pad = new Pad(p.getUniqueId(), game.teamOf(p), b.getLocation(), power, System.currentTimeMillis() + seconds * 1000L);
        pads.add(pad);
        p.playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 1f);
        tasks.add(Bukkit.getScheduler().runTaskLater(skills.plugin(), () -> { pads.remove(pad); restore(pad.block); }, seconds * 20L));
        return true;
    }

    public void flag(Player p, Location at, int seconds) {
        TeamColor t = game.teamOf(p);
        Block b = at.getBlock();
        List<Location> locs = new ArrayList<>();
        if (placeTemp(b, Material.SPRUCE_FENCE)) locs.add(b.getLocation());
        Block top = b.getRelative(BlockFace.UP);
        if (placeTemp(top, t == TeamColor.RED ? Material.RED_WOOL : Material.BLUE_WOOL)) locs.add(top.getLocation());
        restoreLater(locs, seconds);
    }

    // ------------------------------------------------------------ clouds

    public void cloud(Player p, Location at, double radius, int seconds) {
        long until = System.currentTimeMillis() + seconds * 1000L;
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskTimer(skills.plugin(), () -> {
            if (System.currentTimeMillis() > until || !game.isRunning()) { holder[0].cancel(); return; }
            at.getWorld().spawnParticle(Particle.WITCH, at, 40, radius / 2, 0.5, radius / 2, 0);
            at.getWorld().spawnParticle(Particle.SQUID_INK, at, 6, radius / 2, 0.2, radius / 2, 0);
            for (Player e : skills.actives.enemiesNear(p, at, radius)) {
                e.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 0));
                e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
            }
        }, 0L, 20L);
        tasks.add(holder[0]);
        at.getWorld().playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.7f);
    }
}
