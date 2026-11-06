package dev.aetherwar.item;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** Custom sounds from the resource pack (assets/aetherwar/sounds.json). */
public final class Sfx {
    private Sfx() {}

    public static void play(Player p, String name, float volume, float pitch) {
        p.playSound(p.getLocation(), "aetherwar:" + name, SoundCategory.MASTER, volume, pitch);
    }

    public static void at(Location l, String name, float volume, float pitch) {
        l.getWorld().playSound(l, "aetherwar:" + name, SoundCategory.MASTER, volume, pitch);
    }
}
