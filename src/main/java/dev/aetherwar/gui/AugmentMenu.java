package dev.aetherwar.gui;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.item.Sfx;
import dev.aetherwar.skill.PlayerData;
import dev.aetherwar.skill.Skill;
import dev.aetherwar.skill.SkillItems;
import dev.aetherwar.skill.SkillRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Pick 1 of 3 rift augments. */
public class AugmentMenu extends Menu {
    private static final int[] SLOTS = {11, 13, 15};
    private final GameManager game;

    public AugmentMenu(GameManager game, Player viewer) {
        super(viewer);
        this.game = game;
        create(27, title(BG_SMALL, "균열 각인 · 하나를 고르세요", TextColor.color(0x3A2A55)));
    }

    public static void open(GameManager game, Player p) {
        if (game.skills.data(p).pendingAugments.isEmpty()) {
            p.sendMessage(Component.text("고를 균열 각인이 없어요.", NamedTextColor.GRAY));
            return;
        }
        new AugmentMenu(game, p).open();
        Sfx.play(p, "surge", 0.6f, 1.4f);
    }

    @Override
    public void build() {
        inv.clear();
        PlayerData d = game.skills.data(viewer);
        List<String> ids = d.pendingAugments;
        for (int i = 0; i < ids.size() && i < 3; i++) {
            Skill s = SkillRegistry.get(ids.get(i));
            List<Component> lore = SkillItems.describe(s);
            lore.add(Component.empty());
            lore.add(Component.text("클릭해서 고르기", NamedTextColor.YELLOW));
            inv.setItem(SLOTS[i], SkillItems.icon(Material.PAPER, SkillItems.iconOf(s),
                    Component.text("✦ " + s.name, SkillItems.colorOf(s)).decorate(TextDecoration.BOLD), lore, true, null));
        }
        long left = Math.max(0, 30 - (game.elapsed() - d.pendingSince));
        inv.setItem(22, SkillItems.icon(Material.CLOCK, null, Component.text(left + "초 안에 안 고르면 무작위로 정해져요", NamedTextColor.GRAY), List.of(), false, null));
    }

    @Override
    public void click(int slot, ClickType type) {
        PlayerData d = game.skills.data(viewer);
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot || i >= d.pendingAugments.size()) continue;
            Skill s = SkillRegistry.get(d.pendingAugments.get(i));
            game.skills.addAugment(viewer, s);
            viewer.closeInventory();
            viewer.sendMessage(Component.text("✦ 균열 각인: " + s.name, SkillItems.colorOf(s)));
            Sfx.play(viewer, "capture", 1f, 1.5f);
            return;
        }
    }
}
