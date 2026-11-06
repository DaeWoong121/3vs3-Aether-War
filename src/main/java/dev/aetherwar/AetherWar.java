package dev.aetherwar;

import dev.aetherwar.arena.Arena;
import dev.aetherwar.command.RiftCommand;
import dev.aetherwar.game.GameListener;
import dev.aetherwar.game.GameManager;
import dev.aetherwar.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class AetherWar extends JavaPlugin {
    private GameManager game;
    private ResourcePackManager packs;
    private dev.aetherwar.lobby.Lobby lobby;

    public ResourcePackManager packs() { return packs; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Items.init(this);
        dev.aetherwar.skill.SkillItems.init(this);

        Arena arena = new Arena();
        String worldName = getConfig().getString("arena.world", "aether_arena");
        // load the arena world if it was built before
        if (Bukkit.getWorld(worldName) == null && new java.io.File(Bukkit.getWorldContainer(), worldName).isDirectory()) {
            World w = new WorldCreator(worldName).generator(new ChunkGenerator() {}).createWorld();
            if (w != null) getLogger().info("경기 맵 월드를 불러왔어요: " + worldName);
        }
        arena.load(getConfig());
        if (!arena.isReady() && arena.loadFromWorld()) {
            arena.save(getConfig());
            saveConfig();
            getLogger().info("월드 폴더의 맵 정보를 불러왔어요.");
        }

        packs = new ResourcePackManager(this);
        packs.load();
        getServer().getPluginManager().registerEvents(packs, this);

        game = new GameManager(this, arena);
        getServer().getPluginManager().registerEvents(new GameListener(game), this);
        getServer().getPluginManager().registerEvents(new dev.aetherwar.gui.GuiListener(), this);
        lobby = new dev.aetherwar.lobby.Lobby(this, game, arena);
        game.lobby = lobby;
        getServer().getPluginManager().registerEvents(new dev.aetherwar.lobby.LobbyListener(game, lobby), this);
        if (arena.isReady() && !lobby.exists() && getConfig().getBoolean("lobby.auto-build", true)) {
            getLogger().info("로비 섬을 짓는 중...");
            lobby.build();
        }
        lobby.start();
        // players already online after a reload
        getServer().getScheduler().runTask(this, () -> { if (lobby.exists()) lobby.sendAll(); });
        RiftCommand cmd = new RiftCommand(this, game, arena);
        getCommand("aw").setExecutor(cmd);
        getCommand("aw").setTabCompleter(cmd);
        getLogger().info("Aether War 준비 완료. 맵: " + (arena.isReady() ? "준비됨" : "없음 (/aw buildmap)"));
    }

    @Override
    public void onDisable() {
        if (game != null) game.abort();
        if (lobby != null) lobby.shutdown();
    }
}
