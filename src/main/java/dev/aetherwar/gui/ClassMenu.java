package dev.aetherwar.gui;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.game.TeamColor;
import dev.aetherwar.item.Sfx;
import dev.aetherwar.skill.PlayerClass;
import dev.aetherwar.skill.SkillItems;
import dev.aetherwar.skill.SkillManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** Pick one of the four classes before the match. At most 2 of the same class per team. */
public class ClassMenu extends Menu {
    private static final int[] SLOTS = {10, 12, 14, 16};
    private static final String[][] INFO = {
            {"노드 점령과 약탈, 팀원 호위", "돌격 · 수호 · 사냥꾼", "시작 장비: 철 검, 방패, 활"},
            {"빠른 채굴과 대량 운반", "숙련 · 짐꾼 · 탐지", "시작 장비: 다이아 곡괭이, 돌 검, 활"},
            {"결정 정제, 치유, 물약 방해", "정제 · 치유 · 혼돈", "시작 장비: 돌 검, 쇠뇌"},
            {"벽, 포탑, 점프대, 다리", "요새 · 기계 · 길잡이", "시작 장비: 돌 검, 활"},
    };
    private final GameManager game;

    public ClassMenu(GameManager game, Player viewer) {
        super(viewer);
        this.game = game;
        create(27, title(BG_SMALL, "직업 고르기", TextColor.color(0x3A2A55)));
    }

    public static void open(GameManager game, Player p) {
        new ClassMenu(game, p).open();
    }

    @Override
    public void build() {
        inv.clear();
        SkillManager sk = game.skills;
        TeamColor t = game.teamOf(viewer);
        PlayerClass mine = sk.classOf(viewer);
        PlayerClass[] all = PlayerClass.values();
        for (int i = 0; i < all.length; i++) {
            PlayerClass c = all[i];
            int count = t == null ? 0 : sk.countClass(t, c);
            boolean full = mine != c && count >= SkillManager.MAX_PER_CLASS;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(INFO[i][0], NamedTextColor.GRAY));
            lore.add(Component.text("갈래: " + INFO[i][1], c.color));
            lore.add(Component.text(INFO[i][2], NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            if (t != null) {
                List<String> names = new ArrayList<>();
                for (Player m : game.members(t)) if (sk.classOf(m) == c) names.add(m.getName());
                lore.add(Component.text("우리 팀 " + count + "/" + SkillManager.MAX_PER_CLASS + (names.isEmpty() ? "" : " · " + String.join(", ", names)), NamedTextColor.WHITE));
            }
            lore.add(mine == c ? Component.text("✔ 선택함", NamedTextColor.GREEN)
                    : full ? Component.text("자리가 없어요", NamedTextColor.RED)
                    : Component.text("클릭해서 고르기", NamedTextColor.YELLOW));
            inv.setItem(SLOTS[i], SkillItems.icon(c.icon, "class_" + c.key(),
                    Component.text(c.displayName, c.color).decorate(TextDecoration.BOLD), lore, mine == c, null));
        }
    }

    @Override
    public void click(int slot, ClickType type) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            PlayerClass c = PlayerClass.values()[i];
            String err = game.skills.chooseClass(viewer, c);
            if (err != null) {
                viewer.sendMessage(Component.text(err, NamedTextColor.RED));
                viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                return;
            }
            Sfx.play(viewer, "capture", 0.8f, 1.2f);
            TeamColor t = game.teamOf(viewer);
            if (t != null) for (Player m : game.members(t))
                m.sendMessage(Component.text(viewer.getName() + " → " + c.displayName, c.color));
            for (Player m : viewer.getServer().getOnlinePlayers())
                if (m.getOpenInventory().getTopInventory().getHolder() instanceof ClassMenu cm) cm.build();
            return;
        }
    }
}
