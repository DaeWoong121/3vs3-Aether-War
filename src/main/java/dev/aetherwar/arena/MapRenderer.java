package dev.aetherwar.arena;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/** Renders a top-down picture of the arena (like a vanilla map, with height shading) for previews. */
public final class MapRenderer {
    private MapRenderer() {}

    public static File render(World w, int half, int scale, File out) throws IOException {
        int size = (2 * half + 1);
        BufferedImage img = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
        int[][] heights = new int[size][size];
        int[][] colors = new int[size][size];
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                int rgb = 0x101018, h = 0;
                for (int y = MapBuilder.Y + 30; y >= MapBuilder.Y - 10; y--) {
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m.isAir() || m == Material.BARRIER || m == Material.LIGHT) continue;
                    rgb = b.getBlockData().getMapColor().asRGB();
                    if (rgb == 0) continue; // transparent map colour (glass, etc.)
                    h = y;
                    break;
                }
                heights[x + half][z + half] = h;
                colors[x + half][z + half] = rgb;
            }
        }
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int north = j > 0 ? heights[i][j - 1] : heights[i][j];
                double shade = heights[i][j] > north ? 1.12 : heights[i][j] < north ? 0.82 : 1.0;
                int c = colors[i][j];
                int r = clamp((int) (((c >> 16) & 255) * shade)), g = clamp((int) (((c >> 8) & 255) * shade)), b = clamp((int) ((c & 255) * shade));
                int px = (r << 16) | (g << 8) | b;
                for (int dx = 0; dx < scale; dx++) for (int dz = 0; dz < scale; dz++) img.setRGB(i * scale + dx, j * scale + dz, px);
            }
        }
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
