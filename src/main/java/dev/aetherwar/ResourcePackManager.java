package dev.aetherwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Downloads the pack once to learn its SHA-1, then offers it to every player who joins. */
public class ResourcePackManager implements Listener {
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("aetherwar-pack".getBytes());
    private final JavaPlugin plugin;
    private String url;
    private byte[] sha1;

    public ResourcePackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        url = plugin.getConfig().getString("resource-pack.url", "");
        sha1 = null;
        if (url == null || url.isBlank()) return;
        String configured = plugin.getConfig().getString("resource-pack.sha1", "");
        if (configured != null && configured.matches("[0-9a-fA-F]{40}")) {
            sha1 = HexFormat.of().parseHex(configured);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (InputStream in = URI.create(url).toURL().openStream()) {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
                sha1 = md.digest();
                plugin.getLogger().info("리소스팩 준비 완료 (sha1 " + HexFormat.of().formatHex(sha1) + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("리소스팩을 받지 못했어요: " + url + " (" + e.getMessage() + ")");
            }
        });
    }

    public void send(Player p) {
        if (url == null || url.isBlank() || sha1 == null) return;
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", true);
        p.setResourcePack(PACK_ID, url, sha1,
                Component.text("Aether War 전용 리소스팩이에요. 결정, 효과음, 화면 표시가 바뀌어요.", NamedTextColor.LIGHT_PURPLE), required);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> send(e.getPlayer()), 20L);
    }
}
