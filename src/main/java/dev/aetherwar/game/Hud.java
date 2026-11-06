package dev.aetherwar.game;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Boss bars for both cores and a sidebar with time, phase and personal stats. */
public class Hud {
    private final GameManager game;
    private final Scoreboard board;
    private final Map<TeamColor, BossBar> bars = new EnumMap<>(TeamColor.class);
    private Objective sidebar;

    public Hud(GameManager game) {
        this.game = game;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        for (TeamColor t : TeamColor.values()) {
            Team team = board.registerNewTeam(t.name().toLowerCase());
            team.color(t.textColor);
            team.prefix(Component.text("[" + t.displayName + "] ", t.textColor));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
            bars.put(t, BossBar.bossBar(Component.empty(), 0f, t.barColor, BossBar.Overlay.NOTCHED_10));
        }
    }

    public void applyTeam(Player p, TeamColor t) {
        for (Team team : board.getTeams()) team.removeEntry(p.getName());
        board.getTeam(t.name().toLowerCase()).addEntry(p.getName());
        p.setScoreboard(board);
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(board);
            for (BossBar b : bars.values()) p.showBossBar(b);
        }
        update();
    }

    public void stop() {
        for (Player p : Bukkit.getOnlinePlayers()) for (BossBar b : bars.values()) p.hideBossBar(b);
    }

    /** Shows the HUD to someone who joins mid-match. */
    public void show(Player p) {
        p.setScoreboard(board);
        if (game.isRunning()) for (BossBar b : bars.values()) p.showBossBar(b);
    }

    public void update() {
        int target = game.targetEther();
        for (TeamColor t : TeamColor.values()) {
            int e = game.ether(t);
            Component name = Component.text((t == TeamColor.RED ? "\ue010 " : "\ue011 ") + t.displayName + " 코어  ", t.textColor)
                    .append(Component.text("\ue000 " + e + " / " + target, NamedTextColor.WHITE));
            long shield = game.shieldLeft(t);
            if (shield > 0) name = name.append(Component.text("  \ue004 보호막 " + shield + "초", NamedTextColor.AQUA));
            BossBar bar = bars.get(t);
            bar.name(name);
            bar.progress(Math.max(0f, Math.min(1f, e / (float) target)));
        }

        if (sidebar != null) sidebar.unregister();
        sidebar = board.registerNewObjective("aether", Criteria.DUMMY, Component.text("\ue000 AETHER WAR \ue000", NamedTextColor.LIGHT_PURPLE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.numberFormat(NumberFormat.blank());

        long left = Math.max(0, game.totalSeconds() - game.elapsed());
        long phaseLeft = Math.max(0, (game.phase().ordinal() + 1L) * game.phaseSeconds() - game.elapsed());
        List<String> lines = List.of(
                "§f\ue001 §7남은 시간 §f" + mmss(left),
                "§6" + (game.phase().ordinal() + 1) + "페이즈 " + game.phase().displayName + " §7(" + mmss(phaseLeft) + ")",
                "§8" + game.phase().rule,
                " ",
                "§f\ue010 §c홍팀 §f" + game.ether(TeamColor.RED) + "  §f\ue011 §9청팀 §f" + game.ether(TeamColor.BLUE),
                "  ",
                "§7점령 노드 §c" + owned(TeamColor.RED) + " §7: §9" + owned(TeamColor.BLUE),
                game.greatRiftOpen() ? "§d대균열 열림!" : "§5대균열 닫힘"
        );
        int score = lines.size();
        for (String line : lines) sidebar.getScore(line).setScore(score--);
    }

    private int owned(TeamColor t) {
        int n = 0;
        for (RiftNode node : game.nodes()) if (node.owner == t) n++;
        return n;
    }

    private static String mmss(long s) {
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
