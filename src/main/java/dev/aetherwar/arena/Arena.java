package dev.aetherwar.arena;

import dev.aetherwar.game.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Map layout: where the bases, cores and rift nodes are. Stored under "arena" in config.yml. */
public class Arena {
    public record NodeDef(NodeType type, Location location) {}

    public String worldName = "aether_arena";
    public final Map<TeamColor, Location> spawns = new EnumMap<>(TeamColor.class);
    public final Map<TeamColor, Location> cores = new EnumMap<>(TeamColor.class);
    public final List<NodeDef> nodes = new ArrayList<>();
    /** Radius around each team spawn that counts as that team's base. */
    public double baseRadius = 14;
    /** Center of the floating lobby island (floor level), or null when it was not built yet. */
    public Location lobby;

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public boolean isReady() {
        return world() != null && spawns.size() == 2 && cores.size() == 2 && !nodes.isEmpty();
    }

    public String missing() {
        List<String> m = new ArrayList<>();
        if (world() == null) m.add("월드(" + worldName + ")");
        for (TeamColor t : TeamColor.values()) {
            if (!spawns.containsKey(t)) m.add(t.displayName + " 스폰");
            if (!cores.containsKey(t)) m.add(t.displayName + " 코어");
        }
        if (nodes.isEmpty()) m.add("균열 노드");
        return String.join(", ", m);
    }

    /** The team whose base contains this location, or null for neutral ground. */
    public TeamColor baseAt(Location loc) {
        for (TeamColor t : TeamColor.values()) {
            Location s = spawns.get(t);
            if (s != null && s.getWorld() == loc.getWorld() && s.distanceSquared(loc) <= baseRadius * baseRadius) return t;
        }
        return null;
    }

    public void load(FileConfiguration cfg) {
        spawns.clear();
        cores.clear();
        nodes.clear();
        lobby = null;
        ConfigurationSection a = cfg.getConfigurationSection("arena");
        if (a == null) return;
        worldName = a.getString("world", worldName);
        baseRadius = a.getDouble("base-radius", baseRadius);
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        for (TeamColor t : TeamColor.values()) {
            String key = t.name().toLowerCase();
            Location s = readLoc(w, a.getString("spawn." + key));
            if (s != null) spawns.put(t, s);
            Location c = readLoc(w, a.getString("core." + key));
            if (c != null) cores.put(t, c);
        }
        lobby = readLoc(w, a.getString("lobby"));
        for (String line : a.getStringList("nodes")) {
            String[] parts = line.split(" ", 2);
            NodeType type = NodeType.parse(parts[0]);
            Location l = parts.length > 1 ? readLoc(w, parts[1]) : null;
            if (type != null && l != null) nodes.add(new NodeDef(type, l));
        }
    }

    public void save(FileConfiguration cfg) {
        cfg.set("arena.world", worldName);
        cfg.set("arena.base-radius", baseRadius);
        for (TeamColor t : TeamColor.values()) {
            String key = t.name().toLowerCase();
            cfg.set("arena.spawn." + key, writeLoc(spawns.get(t)));
            cfg.set("arena.core." + key, writeLoc(cores.get(t)));
        }
        cfg.set("arena.lobby", writeLoc(lobby));
        List<String> list = new ArrayList<>();
        for (NodeDef n : nodes) list.add(n.type().name().toLowerCase().replace('_', '-') + " " + writeLoc(n.location()));
        cfg.set("arena.nodes", list);
    }

    /** The layout is also stored inside the world folder, so a copied map works without the plugin config. */
    public File worldFile() {
        return new File(new File(Bukkit.getWorldContainer(), worldName), "aetherwar-arena.yml");
    }

    public void saveToWorld() {
        YamlConfiguration y = new YamlConfiguration();
        save(y);
        try { y.save(worldFile()); } catch (IOException ignored) { }
    }

    public boolean loadFromWorld() {
        File f = worldFile();
        if (!f.isFile()) return false;
        load(YamlConfiguration.loadConfiguration(f));
        return isReady();
    }

    private static Location readLoc(World w, String s) {
        if (s == null) return null;
        String[] p = s.trim().split("\\s+");
        if (p.length < 3) return null;
        try {
            double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]), z = Double.parseDouble(p[2]);
            float yaw = p.length > 3 ? Float.parseFloat(p[3]) : 0f;
            return new Location(w, x, y, z, yaw, 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String writeLoc(Location l) {
        if (l == null) return null;
        return l.getX() + " " + l.getY() + " " + l.getZ() + " " + l.getYaw();
    }
}
