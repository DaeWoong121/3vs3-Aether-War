package dev.aetherwar.gui;

import dev.aetherwar.game.GameManager;
import dev.aetherwar.item.Sfx;
import dev.aetherwar.skill.PlayerClass;
import dev.aetherwar.skill.PlayerData;
import dev.aetherwar.skill.Skill;
import dev.aetherwar.skill.SkillItems;
import dev.aetherwar.skill.SkillManager;
import dev.aetherwar.skill.SkillManager.NodeState;
import dev.aetherwar.skill.SkillRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 54-slot skill tree.
 * Columns 0-1 branch A, 2 cross A+B, 3-4 branch B, 5 cross B+C, 6-7 branch C, 8 info / respec / cross A+C.
 * Rows 0-4 are tiers 1-5. The bottom row shows the two active slots and the rift augments.
 */
public class SkillMenu extends Menu {
    private static final int[] BRANCH_COL = {0, 3, 6};
    private static final int INFO = 8, RESPEC = 17, CROSS_AB = 20, CROSS_BC = 23, CROSS_AC = 26, HELP = 35;
    private static final int LOAD0 = 45, LOAD1 = 46, AUG_FIRST = 48;
    private final GameManager game;
    private final Map<Integer, Skill> bySlot = new HashMap<>();

    public SkillMenu(GameManager game, Player viewer) {
        super(viewer);
        this.game = game;
        PlayerClass c = game.skills.classOf(viewer);
        create(54, title(BG_TREE, "스킬트리 · " + (c == null ? "직업 없음" : c.displayName), TextColor.color(0x3A2A55)));
    }

    public static void open(GameManager game, Player p) {
        if (game.skills.classOf(p) == null) {
            ClassMenu.open(game, p);
            return;
        }
        new SkillMenu(game, p).open();
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
    }

    @Override
    public void build() {
        inv.clear();
        bySlot.clear();
        SkillManager sk = game.skills;
        PlayerData d = sk.data(viewer);
        PlayerClass c = d.cls;
        for (int b = 0; b < 3; b++)
            for (int t = 0; t < 5; t++)
                for (int ch = 0; ch < 2; ch++) {
                    Skill s = SkillRegistry.at(c, b, t, ch);
                    if (s != null) put(t * 9 + BRANCH_COL[b] + ch, s);
                }
        put(CROSS_AB, SkillRegistry.cross(c, Skill.CROSS_AB));
        put(CROSS_BC, SkillRegistry.cross(c, Skill.CROSS_BC));
        put(CROSS_AC, SkillRegistry.cross(c, Skill.CROSS_AC));

        int free = sk.freePoints(viewer);
        List<Component> info = new ArrayList<>();
        info.add(Component.text("남은 포인트: " + free, free > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        info.add(Component.text("쓴 포인트: " + d.spent, NamedTextColor.GRAY));
        for (int b = 0; b < 3; b++)
            info.add(Component.text(c.branches[b] + " " + d.picksIn(b) + "/5", TextColor.color(c.branchColors[b])));
        info.add(Component.empty());
        info.add(Component.text("기여도 100마다 1포인트", NamedTextColor.DARK_GRAY));
        info.add(Component.text("(납품, 채굴, 처치, 수호자, 보스)", NamedTextColor.DARK_GRAY));
        inv.setItem(INFO, SkillItems.icon(c.icon, "class_" + c.key(), Component.text(c.displayName + " · " + c.role, c.color).decorate(TextDecoration.BOLD), info, free > 0, null));

        int cost = game.plugin().getConfig().getInt("skills.respec-crystals", 10);
        boolean used = d.respecPhase == game.phase().ordinal();
        inv.setItem(RESPEC, SkillItems.icon(Material.PAPER, "skill_respec", Component.text("스킬 재분배", NamedTextColor.GOLD),
                List.of(Component.text("트리와 교차 스킬을 모두 되돌려요 (각인은 유지)", NamedTextColor.GRAY),
                        Component.text("비용: 결정 " + cost + "개 · 페이즈마다 1번", NamedTextColor.GRAY),
                        used ? Component.text("이번 페이즈에 이미 썼어요", NamedTextColor.RED) : Component.text("쉬프트 + 클릭으로 재분배", NamedTextColor.YELLOW)),
                false, null));

        inv.setItem(HELP, SkillItems.icon(Material.PAPER, "skill_help", Component.text("사용법", NamedTextColor.AQUA),
                List.of(Component.text("단계마다 두 스킬 중 하나만 고를 수 있어요", NamedTextColor.GRAY),
                        Component.text("두 갈래에 2포인트씩 넣으면 사이의 교차 스킬이 열려요", NamedTextColor.GRAY),
                        Component.text("액티브는 좌클릭으로 장착, 우클릭으로 해제", NamedTextColor.GRAY),
                        Component.text("장착한 액티브는 7, 8번 칸에서 우클릭으로 사용", NamedTextColor.GRAY)), false, null));

        for (int i = 0; i < 2; i++) {
            String id = d.loadout[i];
            Skill s = id == null ? null : SkillRegistry.get(id);
            inv.setItem(i == 0 ? LOAD0 : LOAD1, s == null ? SkillItems.emptySlot(i) : SkillItems.activeItem(s, i));
            if (s != null) bySlot.put(i == 0 ? LOAD0 : LOAD1, s);
        }
        for (int i = 0; i < d.augments.size() && AUG_FIRST + i < 54; i++) {
            Skill s = SkillRegistry.get(d.augments.get(i));
            if (s == null) continue;
            List<Component> lore = SkillItems.describe(s);
            if (s.active != null) lore.add(Component.text("좌클릭: 장착", NamedTextColor.YELLOW));
            inv.setItem(AUG_FIRST + i, SkillItems.icon(Material.PAPER, SkillItems.iconOf(s), Component.text("✦ " + s.name, SkillItems.colorOf(s)), lore, true, null));
            bySlot.put(AUG_FIRST + i, s);
        }
    }

    private void put(int slot, Skill s) {
        if (s == null) return;
        bySlot.put(slot, s);
        NodeState st = game.skills.state(viewer, s);
        PlayerData d = game.skills.data(viewer);
        List<Component> lore = new ArrayList<>();
        String kind = s.isCross() ? "교차 스킬" : (s.isUltimate() ? "궁극기 · " : "") + (s.tier + 1) + "단계";
        lore.add(Component.text(kind, NamedTextColor.DARK_GRAY));
        lore.addAll(SkillItems.describe(s));
        lore.add(Component.empty());
        boolean equipped = s.id.equals(d.loadout[0]) || s.id.equals(d.loadout[1]);
        switch (st) {
            case OWNED -> {
                lore.add(Component.text("✔ 배움", NamedTextColor.GREEN));
                if (s.active != null) lore.add(equipped ? Component.text("장착 중 · 우클릭으로 해제", NamedTextColor.AQUA) : Component.text("좌클릭으로 장착", NamedTextColor.YELLOW));
            }
            case AVAILABLE -> lore.add(game.skills.freePoints(viewer) > 0 ? Component.text("클릭해서 배우기 (1포인트)", NamedTextColor.YELLOW) : Component.text("포인트가 모자라요", NamedTextColor.RED));
            case BLOCKED -> lore.add(Component.text("이 단계는 다른 쪽을 골랐어요", NamedTextColor.DARK_RED));
            case LOCKED -> lore.add(Component.text(s.isCross() ? "양쪽 갈래에 2포인트씩 필요" : "앞 단계를 먼저 배우세요", NamedTextColor.DARK_GRAY));
        }
        String model = st == NodeState.LOCKED || st == NodeState.BLOCKED ? "skill_locked" : SkillItems.iconOf(s);
        TextColor col = st == NodeState.LOCKED || st == NodeState.BLOCKED ? NamedTextColor.GRAY : SkillItems.colorOf(s);
        Component name = Component.text((st == NodeState.OWNED ? "✦ " : "") + s.name, col);
        if (s.isUltimate() || s.isCross()) name = name.decorate(TextDecoration.BOLD);
        inv.setItem(slot, SkillItems.icon(Material.PAPER, model, name, lore, st == NodeState.OWNED, null));
    }

    @Override
    public void click(int slot, ClickType type) {
        SkillManager sk = game.skills;
        if (slot == RESPEC) {
            if (!type.isShiftClick()) { viewer.sendActionBar(Component.text("쉬프트 + 클릭으로 재분배해요", NamedTextColor.GRAY)); return; }
            String err = sk.respec(viewer);
            if (err != null) fail(err);
            else { viewer.sendMessage(Component.text("스킬을 모두 되돌렸어요. 다시 골라보세요.", NamedTextColor.GOLD)); Sfx.play(viewer, "phase", 0.6f, 1.6f); }
            build();
            return;
        }
        Skill s = bySlot.get(slot);
        if (s == null) return;
        if (slot == LOAD0 || slot == LOAD1) {
            sk.unequip(viewer, s.id);
            build();
            return;
        }
        NodeState st = sk.state(viewer, s);
        if (s.cls == null || st == NodeState.OWNED) {
            if (s.active == null) return;
            if (type.isRightClick()) sk.unequip(viewer, s.id);
            else sk.equip(viewer, s.id);
            viewer.playSound(viewer.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 1.2f);
            build();
            return;
        }
        String err = sk.learn(viewer, s);
        if (err != null) { fail(err); return; }
        viewer.playSound(viewer.getLocation(), s.isUltimate() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        viewer.sendMessage(Component.text("✦ " + s.name + " 배움", SkillItems.colorOf(s)));
        if (s.isUltimate() || s.isCross()) {
            var t = game.teamOf(viewer);
            if (t != null) for (Player m : game.members(t)) if (m != viewer)
                m.sendMessage(Component.text(viewer.getName() + "님이 " + s.name + "을(를) 배웠어요", SkillItems.colorOf(s)));
        }
        build();
    }

    private void fail(String err) {
        viewer.sendActionBar(Component.text(err, NamedTextColor.RED));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
    }
}
