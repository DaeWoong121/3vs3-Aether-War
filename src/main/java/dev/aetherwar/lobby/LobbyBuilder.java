package dev.aetherwar.lobby;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.Random;

/**
 * Builds the floating lobby island above the arena. Layout (relative to the center, floor at y):
 * monument at the center, red pad at x-9, blue pad at x+9, class pedestals on the north arc (z-9),
 * spawn on the south side (z+11) looking north.
 */
public final class LobbyBuilder {
    public static final int RADIUS = 16;
    public static final int[] PEDESTAL_X = {-6, -2, 2, 6};
    public static final int PEDESTAL_Z = -9;
    public static final int PAD_X = 9;

    private LobbyBuilder() {}

    public static Location spawn(Location c) { return c.clone().add(0.5, 1, 11.5).setDirection(new org.bukkit.util.Vector(0, 0, -1)); }
    public static Location pad(Location c, int side) { return c.clone().add(side * PAD_X + 0.5, 1, 0.5); }
    public static Location pedestal(Location c, int i) { return c.clone().add(PEDESTAL_X[i] + 0.5, 2, PEDESTAL_Z + 0.5); }

    public static void build(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX(), y = center.getBlockY(), cz = center.getBlockZ();
        Random rnd = new Random(7);
        SimplexNoiseGenerator noise = new SimplexNoiseGenerator(11);
        // clear the air above and build the island body
        for (int dx = -RADIUS - 3; dx <= RADIUS + 3; dx++)
            for (int dz = -RADIUS - 3; dz <= RADIUS + 3; dz++) {
                for (int dy = -16; dy <= 12; dy++) set(w, cx + dx, y + dy, cz + dz, Material.AIR);
                double d = Math.sqrt(dx * dx + dz * dz);
                double edge = RADIUS + noise.noise(dx * 0.15, dz * 0.15) * 1.5;
                if (d > edge) continue;
                // floor pattern
                Material floor;
                if (d < 3.2) floor = Material.POLISHED_DEEPSLATE;
                else if (d < 4.2) floor = Material.AMETHYST_BLOCK;
                else if (d > edge - 1.4) floor = Material.POLISHED_BLACKSTONE_BRICKS;
                else if (((int) Math.floor(d) % 4) == 0) floor = Material.PURPUR_BLOCK;
                else floor = ((dx + dz) & 1) == 0 ? Material.CALCITE : Material.SMOOTH_QUARTZ;
                set(w, cx + dx, y, cz + dz, floor);
                // underside: an uneven inverted cone with amethyst veins
                int depth = (int) Math.round((edge - d) * 0.9 + 2 + noise.noise(dx * 0.3, dz * 0.3) * 2);
                for (int k = 1; k <= depth; k++) {
                    Material m = k < 3 ? Material.DEEPSLATE_TILES : (rnd.nextDouble() < 0.07 ? Material.AMETHYST_BLOCK : (k % 3 == 0 ? Material.TUFF : Material.DEEPSLATE));
                    set(w, cx + dx, y - k, cz + dz, m);
                }
                if (rnd.nextDouble() < 0.05 && depth > 2) {
                    Block b = w.getBlockAt(cx + dx, y - depth - 1, cz + dz);
                    b.setType(Material.AMETHYST_CLUSTER, false);
                    BlockData bd = b.getBlockData();
                    if (bd instanceof Directional dir) { dir.setFacing(BlockFace.DOWN); b.setBlockData(bd, false); }
                }
            }
        monument(w, cx, y, cz);
        pad(w, cx - PAD_X, y, cz, Material.RED_CONCRETE, Material.RED_STAINED_GLASS);
        pad(w, cx + PAD_X, y, cz, Material.BLUE_CONCRETE, Material.BLUE_STAINED_GLASS);
        for (int x : PEDESTAL_X) {
            set(w, cx + x, y + 1, cz + PEDESTAL_Z, Material.POLISHED_DEEPSLATE);
            set(w, cx + x, y, cz + PEDESTAL_Z, Material.SEA_LANTERN);
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block b = w.getBlockAt(cx + x + f.getModX(), y, cz + PEDESTAL_Z + f.getModZ());
                b.setType(Material.POLISHED_DEEPSLATE_SLAB, false);
            }
        }
        // a path from the spawn to the monument
        for (int z = 4; z <= 13; z++) for (int x = -1; x <= 1; x++) set(w, cx + x, y, cz + z, x == 0 ? Material.AMETHYST_BLOCK : Material.PURPUR_PILLAR);
        set(w, cx, y, cz + 11, Material.LODESTONE);
        // lamps around the rim
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(a) * (RADIUS - 2.5)), z = (int) Math.round(Math.sin(a) * (RADIUS - 2.5));
            if (Math.abs(x) <= 2 && z > 0) continue;
            set(w, cx + x, y + 1, cz + z, Material.POLISHED_BLACKSTONE_WALL);
            set(w, cx + x, y + 2, cz + z, Material.POLISHED_BLACKSTONE_WALL);
            Block l = w.getBlockAt(cx + x, y + 3, cz + z);
            l.setType(Material.SOUL_LANTERN, false);
        }
        // small flowering bushes
        int[][] bushes = {{-12, -6}, {12, -6}, {-11, 7}, {11, 7}, {-5, 12}, {5, 12}};
        for (int[] b : bushes) {
            set(w, cx + b[0], y + 1, cz + b[1], Material.FLOWERING_AZALEA_LEAVES);
            set(w, cx + b[0] + 1, y + 1, cz + b[1], Material.AZALEA_LEAVES);
            set(w, cx + b[0], y + 2, cz + b[1], Material.FLOWERING_AZALEA_LEAVES);
            set(w, cx + b[0], y + 1, cz + b[1] + 1, Material.MOSS_CARPET);
        }
        // hanging lanterns under the island
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6;
            int x = (int) Math.round(Math.cos(a) * 8), z = (int) Math.round(Math.sin(a) * 8);
            int by = y - 1;
            while (by > y - 20 && w.getBlockAt(cx + x, by, cz + z).getType() != Material.AIR) by--;
            set(w, cx + x, by, cz + z, Material.CHAIN);
            set(w, cx + x, by - 1, cz + z, Material.CHAIN);
            Block l = w.getBlockAt(cx + x, by - 2, cz + z);
            l.setType(Material.SOUL_LANTERN, false);
            if (l.getBlockData() instanceof Lantern lan) { lan.setHanging(true); l.setBlockData(lan, false); }
        }
    }

    private static void monument(World w, int cx, int y, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            set(w, cx + dx, y + 1, cz + dz, Material.POLISHED_BLACKSTONE_BRICKS);
            if (dx == 0 || dz == 0) set(w, cx + dx, y + 2, cz + dz, Material.AMETHYST_BLOCK);
        }
        set(w, cx, y + 3, cz, Material.BUDDING_AMETHYST);
        set(w, cx, y + 4, cz, Material.AMETHYST_BLOCK);
        set(w, cx, y + 5, cz, Material.AMETHYST_BLOCK);
        set(w, cx, y + 6, cz, Material.END_ROD);
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            for (int h : new int[]{3, 5}) {
                Block b = w.getBlockAt(cx + f.getModX(), y + h, cz + f.getModZ());
                b.setType(h == 3 ? Material.LARGE_AMETHYST_BUD : Material.AMETHYST_CLUSTER, false);
                if (b.getBlockData() instanceof Directional d) { d.setFacing(f); b.setBlockData(d, false); }
            }
        }
    }

    private static void pad(World w, int x, int y, int z, Material core, Material ring) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            set(w, x + dx, y, z + dz, edge || (dx == 0 && dz == 0) ? ring : core);
            set(w, x + dx, y - 1, z + dz, edge ? Material.SEA_LANTERN : Material.IRON_BLOCK);
            set(w, x + dx, y - 2, z + dz, Material.IRON_BLOCK);
        }
        set(w, x, y - 1, z, Material.BEACON);
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
