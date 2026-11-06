package dev.aetherwar.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything a player has chosen this match. */
public class PlayerData {
    public PlayerClass cls;
    /** picks[branch][tier] = chosen choice (0/1) or -1. */
    public final int[][] picks = new int[3][5];
    public final Set<String> crosses = new LinkedHashSet<>();
    public final List<String> augments = new ArrayList<>();
    /** Equipped actives (skill ids), at most 2. */
    public final String[] loadout = new String[2];
    public final Map<String, Long> cooldownUntil = new HashMap<>();
    public int spent = 0;
    public int respecPhase = -1;
    public int phoenixPhase = -1;
    /** Augments offered and not yet picked. */
    public List<String> pendingAugments = List.of();
    public long pendingSince = 0;

    public PlayerData() {
        reset();
    }

    public void reset() {
        for (int[] row : picks) Arrays.fill(row, -1);
        crosses.clear();
        loadout[0] = loadout[1] = null;
        spent = 0;
    }

    public int picksIn(int branch) {
        int n = 0;
        for (int c : picks[branch]) if (c >= 0) n++;
        return n;
    }

    /** All skills that count for stats and actives (tree + cross + augments). */
    public List<Skill> owned() {
        List<Skill> out = new ArrayList<>();
        if (cls != null) {
            for (int b = 0; b < 3; b++)
                for (int t = 0; t < 5; t++)
                    if (picks[b][t] >= 0) out.add(SkillRegistry.at(cls, b, t, picks[b][t]));
            for (String id : crosses) out.add(SkillRegistry.get(id));
        }
        for (String id : augments) out.add(SkillRegistry.get(id));
        out.removeIf(s -> s == null);
        return out;
    }

    public boolean owns(String id) {
        for (Skill s : owned()) if (s.id.equals(id)) return true;
        return false;
    }
}
