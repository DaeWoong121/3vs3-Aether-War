package dev.aetherwar.skill;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One node of a skill tree (or a cross skill / augment).
 * branch 0..2 = tree branch; CROSS_AB/BC/AC = cross skill between two branches; AUGMENT = rift augment.
 * tier 0..4, choice 0 or 1 (each tier offers two skills, you take one).
 */
public final class Skill {
    public static final int CROSS_AB = 10, CROSS_BC = 11, CROSS_AC = 12, AUGMENT = 20;

    public final String id;
    public final String name;
    public final List<String> lore;
    public final PlayerClass cls;
    public final int branch, tier, choice;
    public final Map<Stat, Double> stats = new EnumMap<>(Stat.class);
    public Active active;
    public int cooldown;      // seconds
    public double power;      // damage / heal / amount
    public double radius;     // blocks
    public int duration;      // seconds

    Skill(String id, String name, PlayerClass cls, int branch, int tier, int choice, List<String> lore) {
        this.id = id;
        this.name = name;
        this.cls = cls;
        this.branch = branch;
        this.tier = tier;
        this.choice = choice;
        this.lore = lore;
    }

    public boolean isUltimate() {
        return branch < 3 && tier == 4;
    }

    public boolean isCross() {
        return branch >= CROSS_AB && branch <= CROSS_AC;
    }

    /** The two branches a cross skill sits between. */
    public int[] crossBranches() {
        return switch (branch) {
            case CROSS_AB -> new int[]{0, 1};
            case CROSS_BC -> new int[]{1, 2};
            default -> new int[]{0, 2};
        };
    }

    // builder helpers
    Skill stat(Stat s, double v) { stats.put(s, v); return this; }
    Skill act(Active a, int cooldown, double power, double radius, int duration) {
        this.active = a; this.cooldown = cooldown; this.power = power; this.radius = radius; this.duration = duration; return this;
    }
}
