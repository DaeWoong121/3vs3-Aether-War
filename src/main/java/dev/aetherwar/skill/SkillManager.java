package dev.aetherwar.skill;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.TeamColor;
import dev.aetherwar.item.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Classes, skill points, learning rules, passive stats and the per-second passive effects. */
public class SkillManager {
    public static final int MAX_PER_CLASS = 2;
    private final JavaPlugin plugin;
    private final GameManager game;
    private final Map<UUID, PlayerData> data = new HashMap<>();
    private final Map<UUID, Map<Stat, Double>> statCache = new HashMap<>();
    private final Random random = new Random();
    private final NamespacedKey hpKey, kbKey;
    public ActiveSkills actives;
    public Deployables deploy;

    public SkillManager(JavaPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        this.hpKey = new NamespacedKey(plugin, "skill_hp");
        this.kbKey = new NamespacedKey(plugin, "skill_kb");
    }

    public PlayerData data(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), k -> new PlayerData());
    }

    public PlayerClass classOf(Player p) {
        return data(p).cls;
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) clearAttributes(p);
        data.clear();
        statCache.clear();
    }

    // ------------------------------------------------------------ classes

    public int countClass(TeamColor team, PlayerClass c) {
        int n = 0;
        for (Player m : game.members(team)) if (classOf(m) == c) n++;
        return n;
    }

    /** Returns an error message, or null when the class was set. */
    public String chooseClass(Player p, PlayerClass c) {
        if (game.isRunning()) return "경기 중에는 직업을 바꿀 수 없어요.";
        TeamColor t = game.teamOf(p);
        if (t != null && classOf(p) != c && countClass(t, c) >= MAX_PER_CLASS) return "우리 팀에 " + c.displayName + "가 이미 " + MAX_PER_CLASS + "명이에요.";
        data(p).cls = c;
        return null;
    }

    /** Fills in classes for players who did not pick, preferring classes the team lacks. */
    public void autoAssign(List<Player> players) {
        for (Player p : players) {
            if (classOf(p) != null) continue;
            TeamColor t = game.teamOf(p);
            List<PlayerClass> options = new ArrayList<>(List.of(PlayerClass.values()));
            Collections.shuffle(options, random);
            options.sort((a, b) -> countClass(t, a) - countClass(t, b));
            data(p).cls = options.get(0);
            p.sendMessage(Component.text("직업이 자동으로 " + options.get(0).displayName + "(으)로 정해졌어요.", NamedTextColor.GRAY));
        }
    }

    // ------------------------------------------------------------ points & learning

    public int freePoints(Player p) {
        return game.plugin().getConfig().getInt("skills.starting-points", 1) + game.skillPoints(p) - data(p).spent;
    }

    public enum NodeState { OWNED, AVAILABLE, BLOCKED, LOCKED }

    public NodeState state(Player p, Skill s) {
        PlayerData d = data(p);
        if (s.isCross()) {
            if (d.crosses.contains(s.id)) return NodeState.OWNED;
            int[] bs = s.crossBranches();
            return d.picksIn(bs[0]) >= 2 && d.picksIn(bs[1]) >= 2 ? NodeState.AVAILABLE : NodeState.LOCKED;
        }
        int pick = d.picks[s.branch][s.tier];
        if (pick == s.choice) return NodeState.OWNED;
        if (pick >= 0) return NodeState.BLOCKED;
        return d.picksIn(s.branch) == s.tier ? NodeState.AVAILABLE : NodeState.LOCKED;
    }

    /** Returns an error message, or null on success. */
    public String learn(Player p, Skill s) {
        if (!game.isRunning()) return "경기가 시작되면 배울 수 있어요.";
        PlayerData d = data(p);
        if (s.cls != d.cls) return "다른 직업의 스킬이에요.";
        NodeState st = state(p, s);
        if (st == NodeState.OWNED) return null;
        if (st == NodeState.BLOCKED) return "이 단계는 다른 스킬을 골랐어요.";
        if (st == NodeState.LOCKED) return s.isCross() ? "두 가지에 2포인트씩 넣어야 열려요." : "앞 단계를 먼저 배워야 해요.";
        if (freePoints(p) <= 0) return "스킬 포인트가 없어요. 납품, 채굴, 처치로 기여도를 모으세요.";
        if (s.isCross()) d.crosses.add(s.id);
        else d.picks[s.branch][s.tier] = s.choice;
        d.spent++;
        onSkillsChanged(p);
        if (s.active != null) equip(p, s.id);
        return null;
    }

    public String respec(Player p) {
        PlayerData d = data(p);
        int phase = game.phase().ordinal();
        if (d.respecPhase == phase) return "이번 페이즈에는 이미 재분배했어요.";
        int cost = game.plugin().getConfig().getInt("skills.respec-crystals", 10);
        if (Items.countCrystals(p) < cost) return "결정 " + cost + "개가 필요해요.";
        Items.takeCrystals(p, cost);
        d.respecPhase = phase;
        d.reset();
        onSkillsChanged(p);
        for (String id : d.augments) { Skill a = SkillRegistry.get(id); if (a != null && a.active != null) equip(p, id); }
        refreshHotbar(p);
        return null;
    }

    public void addAugment(Player p, Skill s) {
        data(p).augments.add(s.id);
        data(p).pendingAugments = List.of();
        onSkillsChanged(p);
        if (s.active != null) equip(p, s.id);
    }

    public List<Skill> rollAugments(Player p) {
        List<Skill> pool = new ArrayList<>(SkillRegistry.augments());
        pool.removeIf(s -> data(p).augments.contains(s.id));
        Collections.shuffle(pool, random);
        return pool.subList(0, Math.min(3, pool.size()));
    }

    // ------------------------------------------------------------ loadout

    public void equip(Player p, String id) {
        PlayerData d = data(p);
        if (id.equals(d.loadout[0]) || id.equals(d.loadout[1])) return;
        if (d.loadout[0] == null) d.loadout[0] = id;
        else if (d.loadout[1] == null) d.loadout[1] = id;
        else { d.loadout[0] = d.loadout[1]; d.loadout[1] = id; }
        refreshHotbar(p);
    }

    public void unequip(Player p, String id) {
        PlayerData d = data(p);
        for (int i = 0; i < 2; i++) if (id.equals(d.loadout[i])) d.loadout[i] = null;
        refreshHotbar(p);
    }

    public static final int[] ACTIVE_SLOTS = {6, 7};
    public static final int MENU_SLOT = 8;

    public void refreshHotbar(Player p) {
        PlayerData d = data(p);
        for (int i = 0; i < 2; i++) {
            Skill s = d.loadout[i] == null ? null : SkillRegistry.get(d.loadout[i]);
            p.getInventory().setItem(ACTIVE_SLOTS[i], s == null ? SkillItems.emptySlot(i) : SkillItems.activeItem(s, i));
        }
        p.getInventory().setItem(MENU_SLOT, SkillItems.menuItem());
    }

    // ------------------------------------------------------------ stats

    public double stat(Player p, Stat s) {
        Map<Stat, Double> m = statCache.computeIfAbsent(p.getUniqueId(), k -> computeStats(p));
        return m.getOrDefault(s, 0.0);
    }

    /** Highest value of a stat among a team's players (for team-wide passives). */
    public double teamStat(TeamColor t, Stat s) {
        double best = 0;
        if (t == null) return 0;
        for (Player m : game.members(t)) best = Math.max(best, stat(m, s));
        return best;
    }

    public boolean teamHas(TeamColor t, String skillId) {
        if (t == null) return false;
        for (Player m : game.members(t)) if (data(m).owns(skillId)) return true;
        return false;
    }

    public boolean teamHasBranch(TeamColor t, PlayerClass c, int branch) {
        if (t == null) return false;
        for (Player m : game.members(t)) if (classOf(m) == c && data(m).picksIn(branch) > 0) return true;
        return false;
    }

    private Map<Stat, Double> computeStats(Player p) {
        Map<Stat, Double> m = new EnumMap<>(Stat.class);
        for (Skill s : data(p).owned()) {
            for (Map.Entry<Stat, Double> e : s.stats.entrySet()) {
                if (e.getKey() == Stat.CRYSTAL_GEN) {
                    double cur = m.getOrDefault(Stat.CRYSTAL_GEN, 0.0);
                    m.put(Stat.CRYSTAL_GEN, cur == 0 ? e.getValue() : Math.min(cur, e.getValue()));
                } else m.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        return m;
    }

    public void onSkillsChanged(Player p) {
        statCache.remove(p.getUniqueId());
        applyAttributes(p);
        refreshHotbar(p);
    }

    private void applyAttributes(Player p) {
        clearAttributes(p);
        double hp = stat(p, Stat.MAX_HP);
        if (hp != 0) {
            AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
            a.addModifier(new AttributeModifier(hpKey, hp, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
        }
        double kb = stat(p, Stat.KB_RESIST);
        if (kb != 0) p.getAttribute(Attribute.KNOCKBACK_RESISTANCE)
                .addModifier(new AttributeModifier(kbKey, kb, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
    }

    public void clearAttributes(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) a.removeModifier(hpKey);
        AttributeInstance k = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (k != null) k.removeModifier(kbKey);
    }

    // ------------------------------------------------------------ cooldowns

    public long cooldownLeftMs(Player p, Skill s) {
        return Math.max(0, data(p).cooldownUntil.getOrDefault(s.id, 0L) - System.currentTimeMillis());
    }

    public void startCooldown(Player p, Skill s, int slot) {
        double reduce = stat(p, Stat.COOLDOWN_REDUCE);
        if (s.cls == PlayerClass.BUILDER) reduce += stat(p, Stat.BUILD_CD_REDUCE);
        reduce = Math.min(0.5, reduce);
        long ms = (long) (s.cooldown * 1000 * (1 - reduce));
        data(p).cooldownUntil.put(s.id, System.currentTimeMillis() + ms);
        ItemStack it = p.getInventory().getItem(ACTIVE_SLOTS[slot]);
        if (it != null) p.setCooldown(it.getType(), (int) (ms / 50));
    }

    // ------------------------------------------------------------ per-second passives

    private long ticks = 0;

    public void tick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            TeamColor t = game.teamOf(p);
            if (t == null || p.getGameMode() == GameMode.SPECTATOR || classOf(p) == null) continue;
            int speed = (int) stat(p, Stat.SPEED);
            if (stat(p, Stat.NEUTRAL_SPEED) > 0 && game.arena().baseAt(p.getLocation()) == null) speed = Math.max(speed, 1);
            if (stat(p, Stat.PATH_SPEED) > 0 && p.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType() == Material.DIRT_PATH)
                speed = Math.max(speed, (int) stat(p, Stat.PATH_SPEED));
            if (speed > 0) potion(p, PotionEffectType.SPEED, speed - 1, 40);
            if (stat(p, Stat.REGEN) > 0) potion(p, PotionEffectType.REGENERATION, (int) stat(p, Stat.REGEN) - 1, 60);

            int crystals = Items.countCrystals(p);
            if (stat(p, Stat.CARRY_AURA) > 0 && crystals > 0)
                for (Player ally : game.members(t)) if (ally != p && near(ally, p, 8)) potion(ally, PotionEffectType.SPEED, 0, 40);
            if (stat(p, Stat.REPAIR_AURA) > 0) {
                Location core = game.arena().cores.get(t);
                for (Player ally : game.members(t))
                    if (core != null && ally.getWorld() == core.getWorld() && ally.getLocation().distanceSquared(core) < 144) potion(ally, PotionEffectType.REGENERATION, 0, 60);
            }
            if (stat(p, Stat.HEAL_AURA) > 0 && ticks % 3 == 0) {
                for (Player ally : game.members(t)) if (near(ally, p, 6)) heal(ally, stat(p, Stat.HEAL_AURA));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2.2, 0), 1);
            }
            double gen = stat(p, Stat.CRYSTAL_GEN);
            if (gen > 0 && ticks % (long) gen == 0) {
                p.getInventory().addItem(Items.crystal(1));
                p.sendActionBar(Component.text("에테르 결정 +1 (생성)", NamedTextColor.LIGHT_PURPLE));
            }
            double arrows = stat(p, Stat.ARROW_SUPPLY);
            if (arrows > 0 && ticks % 30 == 0) p.getInventory().addItem(new ItemStack(Material.ARROW, (int) arrows));
            if (stat(p, Stat.NODE_COMPASS) > 0) game.showNodeCompass(p);
            if (stat(p, Stat.ENEMY_SENSE) > 0) {
                for (Player e : game.members(t.other()))
                    if (near(e, p, 12) && e.getGameMode() != GameMode.SPECTATOR) {
                        p.sendActionBar(Component.text("⚠ 근처에 적이 있어요!", NamedTextColor.RED));
                        break;
                    }
            }
        }
    }

    // ------------------------------------------------------------ helpers

    public static boolean near(Player a, Player b, double r) {
        return a.getWorld() == b.getWorld() && a.getLocation().distanceSquared(b.getLocation()) <= r * r;
    }

    public static void potion(Player p, PotionEffectType type, int amp, int ticks) {
        PotionEffect cur = p.getPotionEffect(type);
        if (cur != null && cur.getAmplifier() > amp && cur.getDuration() > 0) return;
        p.addPotionEffect(new PotionEffect(type, ticks, amp, true, false, true));
    }

    public static void heal(Player p, double amount) {
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }

    public GameManager game() { return game; }
    public JavaPlugin plugin() { return plugin; }
}
