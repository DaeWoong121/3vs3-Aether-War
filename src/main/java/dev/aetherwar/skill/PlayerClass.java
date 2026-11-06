package dev.aetherwar.skill;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

public enum PlayerClass {
    WARRIOR("전사", "노드 점령, 약탈, 호위", TextColor.color(0xFF6B5B), Material.IRON_SWORD,
            new String[]{"돌격", "수호", "사냥꾼"}, new int[]{0xFFAA3C, 0xC9CED6, 0x8BD45A}),
    MINER("광부", "채굴과 운반", TextColor.color(0xFFC94D), Material.IRON_PICKAXE,
            new String[]{"숙련", "짐꾼", "탐지"}, new int[]{0xFFAA3C, 0x5ADC8C, 0x5ABEFF}),
    ALCHEMIST("연금술사", "정제, 치유, 방해", TextColor.color(0xC08CFF), Material.BREWING_STAND,
            new String[]{"정제", "치유", "혼돈"}, new int[]{0x8FF0FF, 0xFF8FB3, 0x9C6BFF}),
    BUILDER("건축가", "방어 시설과 이동로", TextColor.color(0x5AD1C8), Material.BRICKS,
            new String[]{"요새", "기계", "길잡이"}, new int[]{0xB0B6C0, 0xFF9D4D, 0x6BE07A});

    public final String displayName;
    public final String role;
    public final TextColor color;
    public final Material icon;
    public final String[] branches;
    public final int[] branchColors;

    PlayerClass(String displayName, String role, TextColor color, Material icon, String[] branches, int[] branchColors) {
        this.displayName = displayName;
        this.role = role;
        this.color = color;
        this.icon = icon;
        this.branches = branches;
        this.branchColors = branchColors;
    }

    public String key() {
        return name().toLowerCase();
    }
}
