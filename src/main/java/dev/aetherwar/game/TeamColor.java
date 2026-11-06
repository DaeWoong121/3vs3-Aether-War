package dev.aetherwar.game;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

public enum TeamColor {
    RED("홍팀", NamedTextColor.RED, BossBar.Color.RED, Color.fromRGB(0xC0392B), Material.RED_CONCRETE),
    BLUE("청팀", NamedTextColor.BLUE, BossBar.Color.BLUE, Color.fromRGB(0x2E6FD8), Material.BLUE_CONCRETE);

    public final String displayName;
    public final NamedTextColor textColor;
    public final BossBar.Color barColor;
    public final Color armorColor;
    public final Material floor;

    TeamColor(String displayName, NamedTextColor textColor, BossBar.Color barColor, Color armorColor, Material floor) {
        this.displayName = displayName;
        this.textColor = textColor;
        this.barColor = barColor;
        this.armorColor = armorColor;
        this.floor = floor;
    }

    public TeamColor other() {
        return this == RED ? BLUE : RED;
    }

    public static TeamColor parse(String s) {
        return switch (s.toLowerCase()) {
            case "red", "홍", "홍팀", "r" -> RED;
            case "blue", "청", "청팀", "b" -> BLUE;
            default -> null;
        };
    }
}
