package dev.aetherwar.game;

import dev.aetherwar.arena.Arena;
import dev.aetherwar.arena.NodeType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runtime state of one rift node during a match. */
public class RiftNode {
    public final Arena.NodeDef def;
    /** Server tick (seconds since start) at which the node becomes minable again. */
    public long readyAt = 0;
    /** Owner of a contested node, or null. */
    public TeamColor owner;
    /** Capture progress in seconds and which team is making it. */
    public int captureProgress = 0;
    public TeamColor capturingTeam;
    /** Hits each player has landed on the node toward the next harvest. */
    public final Map<UUID, Integer> hits = new HashMap<>();
    public TextDisplay label;

    public RiftNode(Arena.NodeDef def) {
        this.def = def;
    }

    public NodeType type() {
        return def.type();
    }

    public Location blockLoc() {
        return def.location();
    }

    public void setActive(boolean active) {
        blockLoc().getBlock().setType(active ? Material.AMETHYST_BLOCK : Material.TUFF);
    }

    public boolean isAt(Location l) {
        Location b = blockLoc();
        return l.getWorld() == b.getWorld() && l.getBlockX() == b.getBlockX() && l.getBlockY() == b.getBlockY() && l.getBlockZ() == b.getBlockZ();
    }
}
