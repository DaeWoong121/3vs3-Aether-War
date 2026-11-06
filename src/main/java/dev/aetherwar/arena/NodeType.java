package dev.aetherwar.arena;

import dev.aetherwar.game.TeamColor;

public enum NodeType {
    SAFE_RED("안전 노드", TeamColor.RED),
    SAFE_BLUE("안전 노드", TeamColor.BLUE),
    CONTESTED("점령 노드", null),
    GREAT("대균열", null);

    public final String displayName;
    /** For safe nodes, the only team allowed to mine it. */
    public final TeamColor safeFor;

    NodeType(String displayName, TeamColor safeFor) {
        this.displayName = displayName;
        this.safeFor = safeFor;
    }

    public static NodeType parse(String s) {
        return switch (s.toLowerCase()) {
            case "safe-red", "safe_red" -> SAFE_RED;
            case "safe-blue", "safe_blue" -> SAFE_BLUE;
            case "contested" -> CONTESTED;
            case "great" -> GREAT;
            default -> null;
        };
    }
}
