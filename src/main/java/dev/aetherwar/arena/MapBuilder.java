package dev.aetherwar.arena;

import dev.aetherwar.game.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds the match map from the layout in the design doc, in an empty void world.
 * Layout (top view, x to the right): red base on the left, blue base on the right, two safe nodes in front of
 * each base, two contested nodes above and below the middle, and the great rift in the center.
 * FULL is ~300x300 blocks; SMALL is the same layout at half scale for quick tests.
 */
public final class MapBuilder {
    public enum Size {
        FULL(1.0, true), SMALL(0.5, false);
        final double scale;
        final boolean decorated;
        Size(double scale, boolean decorated) { this.scale = scale; this.decorated = decorated; }
    }

    static final int Y = 64;

    private MapBuilder() {}

    public static World createWorld(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        w = new WorldCreator(name).generator(new ChunkGenerator() {}).createWorld();
        if (w == null) throw new IllegalStateException("월드를 만들 수 없음: " + name);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setTime(6000);
        w.setSpawnLocation(0, Y + 1, 0);
        return w;
    }

    /** Feature positions, scaled. */
    private static final class Layout {
        final double s;
        final int halfX, halfZ, baseX, baseR, safeX, safeZ, contX, contZ, riftR;
        final List<int[]> paths = new ArrayList<>(); // x1,z1,x2,z2

        Layout(double s) {
            this.s = s;
            halfX = (int) (150 * s);
            halfZ = (int) (150 * s);
            baseX = (int) (113 * s);
            baseR = (int) Math.max(11, 22 * s);
            safeX = (int) (64 * s);
            safeZ = (int) (80 * s);
            contX = (int) (25 * s);
            contZ = (int) (102 * s);
            riftR = (int) Math.max(7, 14 * s);
            for (int side : new int[]{-1, 1}) {
                for (int zs : new int[]{-1, 1}) {
                    paths.add(new int[]{side * (baseX - baseR + 4), zs * baseR / 2, side * safeX, zs * safeZ});
                    paths.add(new int[]{side * safeX, zs * safeZ, side * contX, zs * contZ});
                }
            }
            paths.add(new int[]{-baseX, 0, baseX, 0});
            paths.add(new int[]{-contX, -contZ, contX, -contZ});
            paths.add(new int[]{-contX, contZ, contX, contZ});
        }

        /** 0 on a feature, rising to 1 far away; used to flatten terrain near features. */
        double wildness(int x, int z) {
            double d = Double.MAX_VALUE;
            d = Math.min(d, dist(x, z, -baseX, 0) - baseR - 2);
            d = Math.min(d, dist(x, z, baseX, 0) - baseR - 2);
            for (int sx : new int[]{-safeX, safeX}) for (int sz : new int[]{-safeZ, safeZ}) d = Math.min(d, dist(x, z, sx, sz) - 5);
            for (int cx : new int[]{-contX, contX}) for (int cz : new int[]{-contZ, contZ}) d = Math.min(d, dist(x, z, cx, cz) - 6);
            d = Math.min(d, dist(x, z, 0, 0) - riftR - 3);
            for (int[] p : paths) d = Math.min(d, segDist(x, z, p) - 2);
            return Math.max(0, Math.min(1, d / 14.0));
        }

        boolean onPath(int x, int z) {
            for (int[] p : paths) if (segDist(x, z, p) <= 1.5) return true;
            return false;
        }
    }

    private static double dist(double x, double z, double x2, double z2) {
        return Math.hypot(x - x2, z - z2);
    }

    private static double segDist(double x, double z, int[] p) {
        double ax = p[0], az = p[1], bx = p[2], bz = p[3];
        double dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * dx + (z - az) * dz) / len2));
        return dist(x, z, ax + t * dx, az + t * dz);
    }

    /**
     * Builds the map over several ticks to avoid freezing the server, fills in the arena config,
     * then runs {@code done} on the main thread.
     */
    public static void build(World w, Arena arena, Size size, Runnable done) {
        Layout L = new Layout(size.scale);
        SimplexNoiseGenerator noise = new SimplexNoiseGenerator(7L);
        Random rnd = new Random(42);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(MapBuilder.class);

        new BukkitRunnable() {
            int x = -L.halfX - 2;

            @Override
            public void run() {
                for (int i = 0; i < 3 && x <= L.halfX + 2; i++, x++) terrainSlice(w, L, noise, x, size.decorated);
                if (x > L.halfX + 2) {
                    cancel();
                    structures(w, L, arena, size, rnd);
                    done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 0 deep in a team side, 1 in the middle of the neutral zone, with a noisy border. */
    private static double neutralness(Layout L, SimplexNoiseGenerator noise, int x, int z) {
        double edge = L.contX + 20 + noise.noise(z / 18.0, 3.7) * 6;
        double t = (edge + 8 - Math.abs(x)) / 16.0;
        return Math.max(0, Math.min(1, t));
    }

    private static int height(Layout L, SimplexNoiseGenerator noise, int x, int z, boolean decorated) {
        if (!decorated) return Y;
        double wild = L.wildness(x, z);
        double n = noise.noise(x / 45.0, z / 45.0) * 4 + noise.noise(x / 14.0, z / 14.0) * 1.4;
        // hills rise toward the map border so the arena feels like a valley
        double border = Math.min(L.halfX - Math.abs(x), L.halfZ - Math.abs(z));
        double ridge = border < 26 ? Math.pow((26 - border) / 26.0, 1.6) * (13 + noise.noise(x / 20.0, z / 20.0, 9) * 5) : 0;
        return Y + (int) Math.round((n + ridge) * wild);
    }

    private static void terrainSlice(World w, Layout L, SimplexNoiseGenerator noise, int x, boolean decorated) {
        for (int z = -L.halfZ - 2; z <= L.halfZ + 2; z++) {
            for (int y = Y - 8; y <= Y + 30; y++) {
                Block b = w.getBlockAt(x, y, z);
                if (!b.getType().isAir()) b.setType(Material.AIR, false);
            }
            if (Math.abs(x) > L.halfX || Math.abs(z) > L.halfZ) continue;
            boolean edge = Math.abs(x) == L.halfX || Math.abs(z) == L.halfZ;

            int h = height(L, noise, x, z, decorated);
            double neu = decorated ? neutralness(L, noise, x, z) : (Math.abs(x) <= L.contX + 20 ? 1 : 0);
            double patch = noise.noise(x / 9.0, z / 9.0, 2.0);
            double vein = Math.abs(noise.noise(x / 22.0, z / 22.0, 5.0));
            int hash = (x * 73856093) ^ (z * 19349663);

            for (int y = Y - 6; y < h; y++) {
                Material fill = y < h - 3 ? ((hash >>> (y & 15) & 3) == 0 ? Material.ANDESITE : Material.STONE) : Material.DIRT;
                w.getBlockAt(x, y, z).setType(fill, false);
            }

            Material top;
            boolean path = L.onPath(x, z);
            boolean pathEdge = !path && pathNear(L, x, z);
            if (path) top = Material.DIRT_PATH;
            else if (pathEdge) top = (hash & 3) == 0 ? Material.GRAVEL : Material.COARSE_DIRT;
            else if (h >= Y + 14) top = patch > 0.2 ? Material.SNOW_BLOCK : Material.STONE;
            else if (h >= Y + 9) top = patch > 0.3 ? Material.MOSSY_COBBLESTONE : (patch < -0.3 ? Material.ANDESITE : Material.STONE);
            else if (neu > 0.5 || (neu > 0 && ((hash & 255) / 255.0) < neu)) {
                if (vein < 0.035) top = Material.AMETHYST_BLOCK;          // glowing rift veins
                else if (vein < 0.06) top = Material.CALCITE;
                else if (patch > 0.45) top = Material.PODZOL;
                else if (patch < -0.5) top = Material.ROOTED_DIRT;
                else top = Material.MOSS_BLOCK;
            } else {
                top = patch > 0.55 ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            }
            w.getBlockAt(x, h, z).setType(top, false);

            // ponds in the low spots of the team sides
            if (decorated && !path && !pathEdge && h <= Y - 2 && L.wildness(x, z) > 0.8 && neu < 0.3) {
                for (int y = h + 1; y <= Y - 2; y++) w.getBlockAt(x, y, z).setType(Material.WATER, false);
                w.getBlockAt(x, h, z).setType(neu > 0.5 ? Material.CLAY : Material.SAND, false);
                if ((hash & 31) == 7) w.getBlockAt(x, Y - 1, z).setType(Material.LILY_PAD, false);
                h = Y - 2;
                continue;
            }

            if (decorated && !path && !pathEdge && L.wildness(x, z) > 0.3 && h < Y + 9) {
                Block above = w.getBlockAt(x, h + 1, z);
                int r = hash & 255;
                if (neu > 0.5) {
                    if (r < 26) above.setType(Material.MOSS_CARPET, false);
                    else if (r < 30) above.setType(Material.SMALL_AMETHYST_BUD, false);
                    else if (r < 33) above.setType(Material.FERN, false);
                    else if (r == 40) above.setType(Material.AZALEA, false);
                } else {
                    if (r < 40) above.setType(Material.SHORT_GRASS, false);
                    else if (r < 44) above.setType(Material.POPPY, false);
                    else if (r < 48) above.setType(Material.CORNFLOWER, false);
                    else if (r < 51) above.setType(Material.OXEYE_DAISY, false);
                    else if (r < 53) above.setType(Material.ALLIUM, false);
                    else if (r < 56 && patch > 0.2) above.setType(Material.PINK_PETALS, false);
                }
            }
            if (edge) {
                for (int y = h + 1; y <= Y + 30; y++) w.getBlockAt(x, y, z).setType(Material.BARRIER, false);
            }
        }
    }

    private static boolean pathNear(Layout L, int x, int z) {
        for (int[] p : L.paths) if (segDist(x, z, p) <= 2.6) return true;
        return false;
    }

    private static void structures(World w, Layout L, Arena arena, Size size, Random rnd) {
        arena.worldName = w.getName();
        arena.spawns.clear();
        arena.cores.clear();
        arena.nodes.clear();
        arena.baseRadius = L.baseR + 2;

        if (size.decorated) trees(w, L, rnd);
        if (size.decorated) pathLights(w, L);

        base(w, L, arena, TeamColor.RED, -1, size.decorated);
        base(w, L, arena, TeamColor.BLUE, 1, size.decorated);

        for (int sz : new int[]{-L.safeZ, L.safeZ}) {
            safeNode(w, arena, NodeType.SAFE_RED, -L.safeX, sz, TeamColor.RED, rnd);
            safeNode(w, arena, NodeType.SAFE_BLUE, L.safeX, sz, TeamColor.BLUE, rnd);
        }
        for (int cx : new int[]{-L.contX, L.contX}) for (int cz : new int[]{-L.contZ, L.contZ}) contestedNode(w, arena, cx, cz, rnd);
        greatRift(w, L, arena, rnd, size.decorated);
    }

    private static void trees(World w, Layout L, Random rnd) {
        SimplexNoiseGenerator grove = new SimplexNoiseGenerator(99L);
        int placed = 0;
        for (int tries = 0; tries < 2600 && placed < 260; tries++) {
            int x = rnd.nextInt(2 * L.halfX - 8) - L.halfX + 4;
            int z = rnd.nextInt(2 * L.halfZ - 8) - L.halfZ + 4;
            if (L.wildness(x, z) < 1) continue;
            double g = grove.noise(x / 30.0, z / 30.0);
            if (g < -0.15 && rnd.nextInt(4) != 0) continue; // clearings
            int h = w.getHighestBlockYAt(x, z);
            if (h > Y + 10) continue;
            Block ground = w.getBlockAt(x, h, z);
            Material gm = ground.getType();
            if (gm != Material.GRASS_BLOCK && gm != Material.MOSS_BLOCK && gm != Material.PODZOL && gm != Material.COARSE_DIRT) continue;
            boolean neutral = Math.abs(x) <= L.contX + 20;
            TreeType type;
            if (neutral) type = rnd.nextInt(3) == 0 ? TreeType.DARK_OAK : TreeType.AZALEA;
            else if (g > 0.35) type = TreeType.CHERRY;          // pink cherry groves
            else if (g > 0.1) type = TreeType.BIRCH;
            else type = rnd.nextInt(5) == 0 ? TreeType.BIG_TREE : TreeType.TREE;
            ground.setType(type == TreeType.DARK_OAK ? Material.PODZOL : Material.GRASS_BLOCK);
            if (type == TreeType.DARK_OAK) for (int[] o : new int[][]{{1, 0}, {0, 1}, {1, 1}}) w.getBlockAt(x + o[0], h, z + o[1]).setType(Material.PODZOL);
            if (w.generateTree(new Location(w, x, h + 1, z), rnd, type)) placed++;
        }
        // amethyst spires scattered through the rift lands
        for (int i = 0, made = 0; i < 200 && made < 22; i++) {
            int x = rnd.nextInt(2 * L.contX + 36) - L.contX - 18;
            int z = rnd.nextInt(2 * L.halfZ - 30) - L.halfZ + 15;
            if (L.wildness(x, z) < 0.9) continue;
            int h = w.getHighestBlockYAt(x, z);
            if (!w.getBlockAt(x, h, z).getType().isSolid() || h > Y + 8) continue;
            spire(w, x, h + 1, z, 3 + rnd.nextInt(5), Material.AMETHYST_BLOCK, Material.AMETHYST_CLUSTER, rnd);
            made++;
        }
    }

    /** A tapering crystal spire with a few buds. */
    private static void spire(World w, int x, int y0, int z, int height, Material body, Material tip, Random rnd) {
        for (int y = 0; y < height; y++) {
            w.getBlockAt(x, y0 + y, z).setType(body);
            if (y < height / 2) {
                for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                    if (y < height / 3 || rnd.nextBoolean()) w.getBlockAt(x + f.getModX(), y0 + y, z + f.getModZ()).setType(body);
                }
            }
        }
        setFacing(w.getBlockAt(x, y0 + height, z), tip, BlockFace.UP);
        setFacing(w.getBlockAt(x + 1, y0 + height / 2, z), Material.MEDIUM_AMETHYST_BUD, BlockFace.UP);
        setFacing(w.getBlockAt(x, y0 + height / 2, z - 1), Material.SMALL_AMETHYST_BUD, BlockFace.UP);
    }

    /** Lantern posts along the paths, alternating sides. */
    private static void pathLights(World w, Layout L) {
        int k = 0;
        for (int[] p : L.paths) {
            double dx = p[2] - p[0], dz = p[3] - p[1], len = Math.hypot(dx, dz);
            double nx = -dz / len, nz = dx / len;
            for (double t = 10; t < len - 6; t += 16, k++) {
                int side = (k & 1) == 0 ? 1 : -1;
                int x = (int) Math.round(p[0] + dx * t / len + nx * 3 * side);
                int z = (int) Math.round(p[1] + dz * t / len + nz * 3 * side);
                if (L.onPath(x, z)) continue;
                int h = w.getHighestBlockYAt(x, z);
                if (w.getBlockAt(x, h, z).getType() == Material.WATER) continue;
                w.getBlockAt(x, h + 1, z).setType(Material.SPRUCE_FENCE);
                w.getBlockAt(x, h + 2, z).setType(Material.SPRUCE_FENCE);
                w.getBlockAt(x, h + 3, z).setType(Math.abs(x) <= L.contX + 20 ? Material.SOUL_LANTERN : Material.LANTERN);
            }
        }
    }

    private static void base(World w, Layout L, Arena arena, TeamColor team, int side, boolean decorated) {
        int cx = side * L.baseX;
        int r = L.baseR;
        boolean red = team == TeamColor.RED;
        Material wall = red ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
        Material glass = red ? Material.RED_STAINED_GLASS : Material.BLUE_STAINED_GLASS;
        Material pane = red ? Material.RED_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE;
        Material wool = red ? Material.RED_WOOL : Material.BLUE_WOOL;
        Material glazed = red ? Material.RED_GLAZED_TERRACOTTA : Material.BLUE_GLAZED_TERRACOTTA;
        Material carpet = red ? Material.RED_CARPET : Material.BLUE_CARPET;

        for (int x = cx - r - 1; x <= cx + r + 1; x++) {
            for (int z = -r - 1; z <= r + 1; z++) {
                double d = dist(x, z, cx, 0);
                if (d > r + 0.5) continue;
                for (int y = Y - 3; y < Y; y++) w.getBlockAt(x, y, z).setType(Material.STONE, false);
                for (int y = Y + 1; y <= Y + 12; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
                Material floor;
                if (d < 3.5) floor = Material.POLISHED_DEEPSLATE;
                else if (d < 4.6) floor = glazed;
                else if (d / r > 0.9) floor = Material.STONE_BRICKS;
                else if (Math.abs(d - r * 0.55) < 0.6) floor = Material.CHISELED_STONE_BRICKS;
                else floor = ((Math.floorMod(x, 4) < 2) ^ (Math.floorMod(z, 4) < 2)) ? team.floor : (red ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA);
                w.getBlockAt(x, Y, z).setType(floor, false);
                if (d > r - 0.7) {
                    double ang = Math.toDegrees(Math.atan2(z, (x - cx) * -side)); // 0 = toward the map center
                    boolean gate = Math.abs(ang) < 12 || Math.abs(Math.abs(ang) - 60) < 10;
                    if (!gate) {
                        w.getBlockAt(x, Y + 1, z).setType(Material.STONE_BRICKS, false);
                        w.getBlockAt(x, Y + 2, z).setType(wall, false);
                        w.getBlockAt(x, Y + 3, z).setType(pane, false);
                        w.getBlockAt(x, Y + 4, z).setType(Material.STONE_BRICK_SLAB, false);
                    }
                }
            }
        }
        // core: iron base so the beacon beam lights up, team glass tints the beam
        for (int x = cx - 2; x <= cx + 2; x++) for (int z = -2; z <= 2; z++) w.getBlockAt(x, Y + 1, z).setType(Material.POLISHED_DEEPSLATE_SLAB);
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = -1; z <= 1; z++) w.getBlockAt(x, Y + 1, z).setType(Material.IRON_BLOCK);
        w.getBlockAt(cx, Y + 2, 0).setType(Material.BEACON);
        w.getBlockAt(cx, Y + 3, 0).setType(glass);
        for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            w.getBlockAt(cx + c[0], Y + 2, c[1]).setType(Material.AMETHYST_BLOCK);
            setFacing(w.getBlockAt(cx + c[0], Y + 3, c[1]), Material.AMETHYST_CLUSTER, BlockFace.UP);
        }
        // gate towers flanking the main gate
        int gx = cx + side * (r - 1);
        for (int gz : new int[]{-5, 5}) tower(w, gx, gz, wall, wool, decorated ? 9 : 6);
        // workstations behind the core
        int back = cx - side * 7;
        w.getBlockAt(back, Y + 1, 4).setType(Material.CRAFTING_TABLE);
        w.getBlockAt(back, Y + 1, -4).setType(Material.BREWING_STAND);
        w.getBlockAt(back - side, Y + 1, 4).setType(Material.SMITHING_TABLE);
        w.getBlockAt(back - side, Y + 1, -4).setType(Material.CAULDRON);
        w.getBlockAt(back - side, Y + 1, 0).setType(Material.ENCHANTING_TABLE);
        for (int z = -6; z <= 6; z++) if (z != 0) w.getBlockAt(back + side, Y + 1, z).setType(carpet);
        // lantern posts inside the ring
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int lx = cx + (int) Math.round(Math.cos(a) * (r - 3)), lz = (int) Math.round(Math.sin(a) * (r - 3));
            w.getBlockAt(lx, Y + 1, lz).setType(Material.POLISHED_BLACKSTONE_WALL);
            w.getBlockAt(lx, Y + 2, lz).setType(Material.POLISHED_BLACKSTONE_WALL);
            w.getBlockAt(lx, Y + 3, lz).setType(Material.LANTERN);
        }
        int spawnX = cx - side * Math.max(10, r / 2);
        float yaw = side < 0 ? -90f : 90f;
        w.getBlockAt(spawnX, Y, 0).setType(Material.LODESTONE);
        arena.spawns.put(team, new Location(w, spawnX + 0.5, Y + 1, 0.5, yaw, 0));
        arena.cores.put(team, new Location(w, cx, Y + 2, 0));
    }

    private static void tower(World w, int x, int z, Material wall, Material wool, int height) {
        for (int y = Y + 1; y <= Y + height; y++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                Material m = corner ? Material.POLISHED_DEEPSLATE : (y % 3 == 0 ? wall : Material.STONE_BRICKS);
                w.getBlockAt(x + dx, y, z + dz).setType(m);
            }
        }
        int top = Y + height + 1;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            w.getBlockAt(x + dx, top, z + dz).setType(Math.abs(dx) == 1 && Math.abs(dz) == 1 ? Material.STONE_BRICK_WALL : wool);
        w.getBlockAt(x, top + 1, z).setType(Material.LANTERN);
    }

    private static void safeNode(World w, Arena arena, NodeType type, int x, int z, TeamColor team, Random rnd) {
        Material accent = team == TeamColor.RED ? Material.RED_GLAZED_TERRACOTTA : Material.BLUE_GLAZED_TERRACOTTA;
        pad(w, x, z, 3, Material.CALCITE, accent);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) setFacing(w.getBlockAt(x + c[0], Y + 1, z + c[1]), Material.MEDIUM_AMETHYST_BUD, BlockFace.UP);
        for (int i = 0; i < 10; i++) {
            int fx = x + rnd.nextInt(11) - 5, fz = z + rnd.nextInt(11) - 5;
            if (Math.abs(fx - x) > 3 || Math.abs(fz - z) > 3) {
                int h = w.getHighestBlockYAt(fx, fz);
                if (w.getBlockAt(fx, h, fz).getType() == Material.GRASS_BLOCK) w.getBlockAt(fx, h + 1, fz).setType(Material.ALLIUM);
            }
        }
        node(w, arena, type, x, z);
    }

    private static void contestedNode(World w, Arena arena, int x, int z, Random rnd) {
        pad(w, x, z, 4, Material.SMOOTH_BASALT, Material.STONE_BRICKS);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (rnd.nextInt(4) == 0 && (dx != 0 || dz != 0)) w.getBlockAt(x + dx, Y, z + dz).setType(rnd.nextBoolean() ? Material.CRACKED_STONE_BRICKS : Material.MOSSY_STONE_BRICKS);
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int i = 0; i < 4; i++) {
            int px = x + corners[i][0], pz = z + corners[i][1];
            int hgt = 3 + rnd.nextInt(3);
            for (int y = 1; y <= hgt; y++) w.getBlockAt(px, Y + y, pz).setType(y == 1 ? Material.CHISELED_STONE_BRICKS : (rnd.nextInt(3) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS));
            w.getBlockAt(px, Y + hgt + 1, pz).setType(i % 2 == 0 ? Material.SOUL_LANTERN : Material.STONE_BRICK_SLAB);
        }
        node(w, arena, NodeType.CONTESTED, x, z);
        w.getBlockAt(x, Y, z).setType(Material.CRYING_OBSIDIAN);
    }

    private static void greatRift(World w, Layout L, Arena arena, Random rnd, boolean decorated) {
        int r = L.riftR;
        // sunken crater: floor two blocks down with a step ring
        for (int x = -r - 2; x <= r + 2; x++) {
            for (int z = -r - 2; z <= r + 2; z++) {
                double d = dist(x, z, 0, 0);
                if (d > r + 1.5) continue;
                for (int y = Y - 2; y <= Y + 12; y++) w.getBlockAt(x, y, z).setType(Material.AIR, false);
                int floorY = d > r ? Y : (d > r - 1.5 ? Y - 1 : Y - 2);
                Material m;
                if (d > r) m = Material.CRYING_OBSIDIAN;
                else if (d > r - 1.5) m = Material.OBSIDIAN;
                else if (d < 1.5) m = Material.END_STONE_BRICKS;
                else if (Math.abs(d - r * 0.5) < 0.6) m = Material.AMETHYST_BLOCK;
                else m = ((x + z) & 1) == 0 ? Material.PURPUR_BLOCK : Material.PURPUR_PILLAR;
                for (int y = Y - 4; y < floorY; y++) w.getBlockAt(x, y, z).setType(Material.OBSIDIAN, false);
                w.getBlockAt(x, floorY, z).setType(m, false);
            }
        }
        // central beacon with a magenta beam: a landmark visible from both bases
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) w.getBlockAt(x, Y - 4, z).setType(Material.IRON_BLOCK);
        w.getBlockAt(0, Y - 3, 0).setType(Material.BEACON);
        w.getBlockAt(0, Y - 2, 0).setType(Material.MAGENTA_STAINED_GLASS);
        if (decorated) {
            for (int i = 0; i < 7; i++) {
                double a = Math.PI * 2 * i / 7 + 0.3;
                int sx = (int) Math.round(Math.cos(a) * (r + 4)), sz = (int) Math.round(Math.sin(a) * (r + 4));
                if (pathNear(L, sx, sz) || pathNear(L, sx + 1, sz) || pathNear(L, sx - 1, sz)) continue;
                int h = 4 + rnd.nextInt(6);
                for (int y = 1; y <= h; y++) {
                    w.getBlockAt(sx, Y + y, sz).setType(y > h - 2 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
                    if (y <= h / 2) for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
                        w.getBlockAt(sx + f.getModX(), Y + y, sz + f.getModZ()).setType(Material.OBSIDIAN);
                }
                setFacing(w.getBlockAt(sx, Y + h + 1, sz), Material.AMETHYST_CLUSTER, BlockFace.UP);
            }
        }
        int o = Math.max(3, r / 2);
        for (int[] c : new int[][]{{o, 0}, {-o, 0}, {0, o}, {0, -o}}) {
            w.getBlockAt(c[0], Y - 2, c[1]).setType(Material.CRYING_OBSIDIAN);
            w.getBlockAt(c[0], Y - 1, c[1]).setType(Material.AMETHYST_BLOCK);
            arena.nodes.add(new Arena.NodeDef(NodeType.GREAT, new Location(w, c[0], Y - 1, c[1])));
        }
    }

    private static void pad(World w, int x, int z, int r, Material floor, Material rim) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            for (int y = Y + 1; y <= Y + 12; y++) w.getBlockAt(x + dx, y, z + dz).setType(Material.AIR, false);
            for (int y = Y - 3; y < Y; y++) w.getBlockAt(x + dx, y, z + dz).setType(Material.STONE, false);
            boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
            w.getBlockAt(x + dx, Y, z + dz).setType(edge ? rim : floor, false);
        }
    }

    private static void node(World w, Arena arena, NodeType type, int x, int z) {
        w.getBlockAt(x, Y + 1, z).setType(Material.AMETHYST_BLOCK);
        arena.nodes.add(new Arena.NodeDef(type, new Location(w, x, Y + 1, z)));
    }

    private static void setFacing(Block b, Material m, BlockFace face) {
        b.setType(m, false);
        if (b.getBlockData() instanceof Directional d) {
            d.setFacing(face);
            b.setBlockData(d, false);
        }
    }
}
