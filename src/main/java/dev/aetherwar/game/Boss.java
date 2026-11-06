package dev.aetherwar.game;

import dev.aetherwar.AetherWar;
import dev.aetherwar.arena.NodeType;
import dev.aetherwar.item.Items;
import dev.aetherwar.item.Sfx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** "균열의 군주": appears in the great rift at the start of phase 3. Last hit wins a big reward and opens the rift. */
public class Boss {
    private static final TextColor PURPLE = TextColor.color(0xC77DFF);

    private final AetherWar plugin;
    private final GameManager game;
    private WitherSkeleton entity;
    private BossBar bar;
    private Location center;
    private boolean defeated;
    private int ticks;
    private final Map<TeamColor, Double> damage = new EnumMap<>(TeamColor.class);
    private final Map<UUID, Double> playerDamage = new java.util.HashMap<>();
    private final Set<UUID> minions = new HashSet<>();
    private UUID lastAttacker;

    public Boss(AetherWar plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    public boolean defeated() { return defeated; }
    public boolean alive() { return entity != null && !entity.isDead(); }

    public boolean is(Entity e) {
        return e != null && ((entity != null && e.getUniqueId().equals(entity.getUniqueId())) || minions.contains(e.getUniqueId()));
    }

    public boolean isMain(Entity e) {
        return e != null && entity != null && e.getUniqueId().equals(entity.getUniqueId());
    }

    private Location findCenter() {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (RiftNode node : game.nodes()) {
            if (node.type() != NodeType.GREAT) continue;
            Location l = node.blockLoc();
            x += l.getX(); y += l.getY(); z += l.getZ(); n++;
        }
        if (n == 0) {
            Location a = game.arena().cores.get(TeamColor.RED), b = game.arena().cores.get(TeamColor.BLUE);
            return a.clone().add(b).multiply(0.5);
        }
        return new Location(game.arena().world(), x / n + 0.5, y / n + 1, z / n + 0.5);
    }

    public void spawn() {
        if (entity != null || defeated || !plugin.getConfig().getBoolean("boss.enabled", true)) return;
        center = findCenter();
        double hp = plugin.getConfig().getDouble("boss.health", 500);
        entity = center.getWorld().spawn(center, WitherSkeleton.class, w -> {
            w.customName(Component.text("균열의 군주", PURPLE).decorate(TextDecoration.BOLD));
            w.setCustomNameVisible(true);
            w.setPersistent(true);
            w.setRemoveWhenFarAway(false);
            w.setCanPickupItems(false);
            set(w, Attribute.MAX_HEALTH, hp);
            w.setHealth(hp);
            set(w, Attribute.SCALE, 1.8);
            set(w, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            set(w, Attribute.ATTACK_DAMAGE, 4);
            set(w, Attribute.MOVEMENT_SPEED, 0.26);
            set(w, Attribute.FOLLOW_RANGE, 20);
            EntityEquipment eq = w.getEquipment();
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            eq.setHelmet(new ItemStack(Material.AMETHYST_CLUSTER));
            eq.setItemInMainHandDropChance(0);
            eq.setHelmetDropChance(0);
            w.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        });
        bar = BossBar.bossBar(Component.text("✦ 균열의 군주 ✦", PURPLE), 1f, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bar);
        center.getWorld().strikeLightningEffect(center);
        center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center, 300, 3, 2, 3, 0.2);
        game.broadcastTitle(Component.text("균열의 군주 등장", PURPLE), Component.text("막타를 친 팀이 대량의 결정과 팀 버프를 얻어요", NamedTextColor.GRAY));
        for (Player p : Bukkit.getOnlinePlayers()) Sfx.play(p, "rift_open", 1f, 0.7f);
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        var inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    public void showTo(Player p) {
        if (bar != null && alive()) p.showBossBar(bar);
    }

    /** Called every second while the game runs. */
    public void tick() {
        if (!alive()) return;
        ticks++;
        bar.progress((float) Math.max(0, Math.min(1, entity.getHealth() / entity.getAttribute(Attribute.MAX_HEALTH).getValue())));
        // leash to the crater
        if (entity.getLocation().distanceSquared(center) > 14 * 14) {
            entity.getWorld().spawnParticle(Particle.PORTAL, entity.getLocation(), 60, 0.5, 1, 0.5, 0.5);
            entity.teleport(center);
            if (entity instanceof Mob m) m.setTarget(null);
        }
        entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, 2, 0), 6, 0.6, 0.8, 0.6, 0);
        List<Player> near = targets(16);
        if (near.isEmpty()) {
            // heal slowly when nobody is fighting
            entity.setHealth(Math.min(entity.getAttribute(Attribute.MAX_HEALTH).getValue(), entity.getHealth() + 5));
            return;
        }
        if (ticks % 8 == 0) shockwave();
        if (ticks % 15 == 5) summon();
        if (ticks % 20 == 10) crystalRain();
    }

    private List<Player> targets(double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (game.teamOf(p) == null || p.getGameMode() == GameMode.SPECTATOR || p.getWorld() != entity.getWorld()) continue;
            if (p.getLocation().distanceSquared(entity.getLocation()) <= r * r) out.add(p);
        }
        return out;
    }

    private void shockwave() {
        Location c = entity.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.8f);
        for (double r = 1; r <= 6; r += 1) dev.aetherwar.skill.ActiveSkills.ring(c.clone().add(0, 0.2, 0), r, Particle.SONIC_BOOM, (int) (r * 3));
        for (Player p : targets(6)) {
            p.damage(4, entity);
            Vector v = p.getLocation().toVector().subtract(c.toVector()).setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
            p.setVelocity(v.normalize().multiply(1.4).setY(0.6));
        }
    }

    private void summon() {
        minions.removeIf(id -> Bukkit.getEntity(id) == null);
        if (minions.size() >= 4) return;
        for (int i = 0; i < 2; i++) {
            Location at = entity.getLocation().add(Math.random() * 4 - 2, 1.5, Math.random() * 4 - 2);
            Vex v = at.getWorld().spawn(at, Vex.class, x -> {
                x.customName(Component.text("균열 잔영", PURPLE));
                x.setPersistent(false);
                set(x, Attribute.MAX_HEALTH, 8);
                x.setHealth(8);
                x.setLimitedLifetimeTicks(20 * 25);
                x.getEquipment().setItemInMainHandDropChance(0);
            });
            minions.add(v.getUniqueId());
        }
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1f);
    }

    /** Crystals rain down around the crater: a reason for miners to come close. */
    private void crystalRain() {
        Location c = center.clone();
        c.getWorld().playSound(c, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.6f);
        for (int i = 0; i < 8; i++) {
            double a = Math.random() * Math.PI * 2, r = 4 + Math.random() * 8;
            Location at = c.clone().add(Math.cos(a) * r, 8, Math.sin(a) * r);
            at.getWorld().dropItem(at, Items.crystal(1));
            at.getWorld().spawnParticle(Particle.END_ROD, at, 10, 0.1, 2, 0.1, 0.02);
        }
        game.broadcast(Component.text("✦ 균열의 군주가 결정을 흩뿌렸어요!", PURPLE));
    }

    public void onDamaged(Player attacker, double amount) {
        TeamColor t = game.teamOf(attacker);
        if (t == null) return;
        damage.merge(t, amount, Double::sum);
        playerDamage.merge(attacker.getUniqueId(), amount, Double::sum);
        lastAttacker = attacker.getUniqueId();
    }

    public void died(Player killer) {
        if (defeated) return;
        defeated = true;
        Player k = killer != null ? killer : (lastAttacker != null ? Bukkit.getPlayer(lastAttacker) : null);
        TeamColor t = k == null ? null : game.teamOf(k);
        Location c = entity.getLocation();
        removeBar();
        for (UUID id : minions) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        minions.clear();
        c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c, 3);
        c.getWorld().spawnParticle(Particle.END_ROD, c, 200, 2, 3, 2, 0.3);
        c.getWorld().playSound(c, Sound.ENTITY_WITHER_DEATH, 1.5f, 1.2f);
        for (Map.Entry<UUID, Double> e : playerDamage.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) game.addContribution(p, (int) Math.min(80, e.getValue() / 3));
        }
        if (t == null) {
            game.broadcast(Component.text("균열의 군주가 쓰러졌어요.", PURPLE));
            return;
        }
        int ether = plugin.getConfig().getInt("boss.reward-ether", 120);
        int crystals = plugin.getConfig().getInt("boss.reward-crystals", 8);
        int buff = plugin.getConfig().getInt("boss.buff-seconds", 180);
        game.addEther(t, ether);
        game.setBossBuff(t, buff);
        for (Player m : game.members(t)) {
            m.getInventory().addItem(Items.crystal(crystals)).values().forEach(left -> m.getWorld().dropItem(m.getLocation(), left));
            m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, buff * 20, 0));
            m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buff * 20, 0));
            m.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
            Sfx.play(m, "victory", 1f, 1.2f);
        }
        game.addContribution(k, 60);
        game.broadcast(Component.text("✦ " + t.displayName + " " + k.getName() + "님이 균열의 군주를 쓰러뜨렸어요! 에테르 +" + ether + ", 팀원 결정 +" + crystals + ", " + (buff / 60) + "분 동안 힘·신속·납품 +25%", t.textColor));
        game.broadcastTitle(Component.text(t.displayName + " 군주 처치!", t.textColor),
                Component.text(k.getName() + " 막타 · 에테르 +" + ether + " · 팀원 결정 +" + crystals + " · " + (buff / 60) + "분 버프", NamedTextColor.GRAY));
    }

    private void removeBar() {
        if (bar != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    public void remove() {
        removeBar();
        if (entity != null) entity.remove();
        for (UUID id : minions) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        minions.clear();
        entity = null;
    }
}
