package dev.aetherwar.gui;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.TeamColor;
import dev.aetherwar.skill.SkillItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** Red / random / blue. */
public class TeamMenu extends Menu {
    private final GameManager game;

    public TeamMenu(GameManager game, Player viewer) {
        super(viewer);
        this.game = game;
        create(27, title(BG_AUGMENT, "팀 고르기", TextColor.color(0x3A2A55)));
    }

    public static void open(GameManager game, Player p) {
        new TeamMenu(game, p).open();
    }

    @Override
    public void build() {
        inv.clear();
        inv.setItem(11, teamItem(TeamColor.RED, Material.RED_BANNER));
        inv.setItem(15, teamItem(TeamColor.BLUE, Material.BLUE_BANNER));
        inv.setItem(13, SkillItems.icon(Material.WHITE_BANNER, null, Component.text("아무 팀이나", NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                List.of(Component.text("인원이 적은 팀으로 들어가요", NamedTextColor.GRAY)), false, null));
    }

    private org.bukkit.inventory.ItemStack teamItem(TeamColor t, Material m) {
        List<Component> lore = new ArrayList<>();
        for (Player p : game.members(t)) lore.add(Component.text("· " + p.getName(), NamedTextColor.WHITE));
        if (lore.isEmpty()) lore.add(Component.text("아직 아무도 없어요", NamedTextColor.DARK_GRAY));
        lore.add(Component.empty());
        lore.add(game.teamOf(viewer) == t ? Component.text("✔ 지금 이 팀", NamedTextColor.GREEN) : Component.text("클릭해서 참가", NamedTextColor.YELLOW));
        return SkillItems.icon(m, null, Component.text(t.displayName + " (" + game.members(t).size() + "명)", t.textColor).decorate(TextDecoration.BOLD), lore, game.teamOf(viewer) == t, null);
    }

    @Override
    public void click(int slot, ClickType type) {
        TeamColor t = switch (slot) {
            case 11 -> TeamColor.RED;
            case 15 -> TeamColor.BLUE;
            case 13 -> game.members(TeamColor.RED).size() <= game.members(TeamColor.BLUE).size() ? TeamColor.RED : TeamColor.BLUE;
            default -> null;
        };
        if (t == null) return;
        if (game.lobby != null) game.lobby.joinTeam(viewer, t, true);
        else game.joinTeam(viewer, t);
        viewer.closeInventory();
    }
}
