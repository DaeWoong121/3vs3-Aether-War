package dev.aetherwar.command;

import dev.aetherwar.AetherWar;
import dev.aetherwar.arena.Arena;
import dev.aetherwar.arena.MapBuilder;
import dev.aetherwar.arena.NodeType;
import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class RiftCommand implements CommandExecutor, TabCompleter {
    private final AetherWar plugin;
    private final GameManager game;
    private final Arena arena;

    public RiftCommand(AetherWar plugin, GameManager game, Arena arena) {
        this.plugin = plugin;
        this.game = game;
        this.arena = arena;
    }

    private static void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("team")) {
            if (!(sender instanceof Player p)) return true;
            TeamColor t = args.length > 1 ? TeamColor.parse(args[1]) : null;
            if (t == null) { msg(sender, "/aw team <red|blue>", NamedTextColor.RED); return true; }
            if (!game.joinTeam(p, t)) msg(sender, "경기 중에는 팀을 바꿀 수 없어요.", NamedTextColor.RED);
            else msg(sender, t.displayName + "에 들어갔어요.", t.textColor);
            return true;
        }
        if (sub.equals("status")) {
            msg(sender, "상태: " + game.state() + " / 페이즈: " + game.phase().displayName + " / 경과 " + game.elapsed() / 60 + "분", NamedTextColor.GRAY);
            msg(sender, "맵: " + (arena.isReady() ? "준비됨 (노드 " + arena.nodes.size() + "개)" : "미완성 - " + arena.missing()), NamedTextColor.GRAY);
            return true;
        }
        if (!sender.hasPermission("aetherwar.admin")) {
            msg(sender, "권한이 없어요.", NamedTextColor.RED);
            return true;
        }
        switch (sub) {
            case "start" -> {
                String err = game.start();
                if (err != null) msg(sender, err, NamedTextColor.RED);
            }
            case "stop" -> game.abort();
            case "skip" -> game.skipPhase();
            case "time" -> {
                if (args.length < 2) { msg(sender, "/aw time <경과 분>", NamedTextColor.RED); return true; }
                try { game.setElapsedMinutes(Integer.parseInt(args[1])); }
                catch (NumberFormatException e) { msg(sender, "숫자를 넣어주세요.", NamedTextColor.RED); }
            }
            case "buildmap", "testmap" -> {
                if (game.isRunning()) { msg(sender, "경기 중에는 맵을 다시 지을 수 없어요.", NamedTextColor.RED); return true; }
                boolean small = sub.equals("testmap");
                msg(sender, (small ? "테스트 맵" : "경기 맵") + "을 짓는 중... 잠시 렉이 생길 수 있어요.", NamedTextColor.YELLOW);
                World w = MapBuilder.createWorld(plugin.getConfig().getString("arena.world", "aether_arena"));
                MapBuilder.build(w, arena, small ? MapBuilder.Size.SMALL : MapBuilder.Size.FULL, () -> {
                    arena.save(plugin.getConfig());
                    plugin.saveConfig();
                    arena.saveToWorld();
                    w.save();
                    msg(sender, "맵 완성! /aw tp red 로 가볼 수 있어요.", NamedTextColor.GREEN);
                });
            }
            case "tp" -> {
                if (!(sender instanceof Player p)) return true;
                TeamColor t = args.length > 1 ? TeamColor.parse(args[1]) : TeamColor.RED;
                Location s = t == null ? null : arena.spawns.get(t);
                if (s == null) msg(sender, "스폰이 설정되지 않았어요.", NamedTextColor.RED);
                else p.teleport(s);
            }
            case "render" -> {
                World w = arena.world();
                if (w == null) { msg(sender, "맵이 없어요.", NamedTextColor.RED); return true; }
                try {
                    java.io.File f = dev.aetherwar.arena.MapRenderer.render(w, 152, 3, new java.io.File(plugin.getDataFolder(), "map.png"));
                    msg(sender, "저장: " + f.getPath(), NamedTextColor.GREEN);
                } catch (java.io.IOException e) {
                    msg(sender, "실패: " + e.getMessage(), NamedTextColor.RED);
                }
            }
            case "setup" -> setup(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                arena.load(plugin.getConfig());
                plugin.packs().load();
                msg(sender, "설정을 다시 불러왔어요.", NamedTextColor.GREEN);
            }
            default -> help(sender);
        }
        return true;
    }

    private void setup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return;
        if (args.length < 2) {
            msg(p, "/aw setup spawn <red|blue> · core <red|blue> · node <safe-red|safe-blue|contested|great> · clearnodes · save", NamedTextColor.GRAY);
            return;
        }
        arena.worldName = p.getWorld().getName();
        Block target = p.getTargetBlockExact(6);
        switch (args[1].toLowerCase()) {
            case "spawn" -> {
                TeamColor t = args.length > 2 ? TeamColor.parse(args[2]) : null;
                if (t == null) return;
                arena.spawns.put(t, p.getLocation().toBlockLocation().add(0.5, 0, 0.5));
                arena.spawns.get(t).setYaw(p.getLocation().getYaw());
                msg(p, t.displayName + " 스폰 지정", NamedTextColor.GREEN);
            }
            case "core" -> {
                TeamColor t = args.length > 2 ? TeamColor.parse(args[2]) : null;
                if (t == null || target == null) { msg(p, "코어로 쓸 블록을 바라보고 입력하세요.", NamedTextColor.RED); return; }
                arena.cores.put(t, target.getLocation());
                msg(p, t.displayName + " 코어 지정: " + target.getType(), NamedTextColor.GREEN);
            }
            case "node" -> {
                NodeType type = args.length > 2 ? NodeType.parse(args[2]) : null;
                if (type == null || target == null) { msg(p, "노드 블록을 바라보고 타입을 넣으세요.", NamedTextColor.RED); return; }
                arena.nodes.add(new Arena.NodeDef(type, target.getLocation()));
                msg(p, type.displayName + " 추가 (총 " + arena.nodes.size() + "개)", NamedTextColor.GREEN);
            }
            case "clearnodes" -> { arena.nodes.clear(); msg(p, "노드를 모두 지웠어요.", NamedTextColor.YELLOW); }
            case "save" -> {
                arena.save(plugin.getConfig());
                plugin.saveConfig();
                arena.saveToWorld();
                msg(p, "맵 설정 저장 완료", NamedTextColor.GREEN);
            }
            default -> msg(p, "알 수 없는 setup 명령", NamedTextColor.RED);
        }
    }

    private void help(CommandSender s) {
        msg(s, "— Aether War —", NamedTextColor.LIGHT_PURPLE);
        msg(s, "/aw team <red|blue>  팀 고르기", NamedTextColor.GRAY);
        msg(s, "/aw status  상태 보기", NamedTextColor.GRAY);
        if (s.hasPermission("aetherwar.admin")) {
            msg(s, "/aw buildmap  경기 맵 짓기 · /aw testmap  작은 테스트 맵", NamedTextColor.GRAY);
            msg(s, "/aw start · stop · skip · time <분> · tp <팀> · setup · reload", NamedTextColor.GRAY);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("team", "status", "start", "stop", "skip", "time", "buildmap", "testmap", "tp", "setup", "reload", "render"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("team") || args[0].equalsIgnoreCase("tp"))) return filter(List.of("red", "blue"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("setup")) return filter(List.of("spawn", "core", "node", "clearnodes", "save"), args[1]);
        if (args.length == 3 && args[1].equalsIgnoreCase("node")) return filter(List.of("safe-red", "safe-blue", "contested", "great"), args[2]);
        if (args.length == 3 && (args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("core"))) return filter(List.of("red", "blue"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase())).toList();
    }
}
