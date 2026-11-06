package dev.aetherwar.game;

public enum Phase {
    EXPLORE("탐험", "PvP 꺼짐"),
    EXPAND("확장", "중립 지역 PvP"),
    CLASH("결전", "전 지역 PvP, 코어 약탈 가능");

    public final String displayName;
    public final String rule;

    Phase(String displayName, String rule) {
        this.displayName = displayName;
        this.rule = rule;
    }
}
