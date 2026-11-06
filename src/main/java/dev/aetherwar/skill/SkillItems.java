package dev.aetherwar.skill;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Hotbar items for active skills, the skill menu item, and GUI icons. Icons come from the resource pack. */
public final class SkillItems {
    private static NamespacedKey key;

    private SkillItems() {}

    public static void init(Plugin plugin) {
        key = new NamespacedKey(plugin, "skill_item");
    }

    public static String tag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /** Resource-pack icon name for a skill: per class branch, cross, ultimate or augment. */
    public static String iconOf(Skill s) {
        if (s.cls == null) return "skill_augment";
        if (s.isCross()) return "skill_" + s.cls.key() + "_cross";
        return "skill_" + s.cls.key() + "_" + s.branch + (s.isUltimate() ? "_ult" : "");
    }

    public static ItemStack icon(Material base, String model, Component name, List<Component> lore, boolean glint, String tag) {
        ItemStack it = new ItemStack(base);
        ItemMeta m = it.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (Component c : lore) l.add(c.decoration(TextDecoration.ITALIC, false));
        m.lore(l);
        if (model != null) {
            var cmd = m.getCustomModelDataComponent();
            cmd.setStrings(List.of(model));
            m.setCustomModelDataComponent(cmd);
        }
        if (glint) m.setEnchantmentGlintOverride(true);
        m.addItemFlags(ItemFlag.values());
        m.setMaxStackSize(1);
        if (tag != null) m.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
        it.setItemMeta(m);
        return it;
    }

    public static TextColor colorOf(Skill s) {
        if (s.cls == null) return TextColor.color(0xFF8FE0);
        if (s.isCross()) return TextColor.color(0xFF78FF);
        return TextColor.color(s.cls.branchColors[s.branch]);
    }

    public static List<Component> describe(Skill s) {
        List<Component> lore = new ArrayList<>();
        for (String line : s.lore) lore.add(Component.text(line, NamedTextColor.GRAY));
        if (s.active != null) {
            lore.add(Component.empty());
            lore.add(Component.text("액티브 · 재사용 " + s.cooldown + "초", NamedTextColor.AQUA));
        } else {
            lore.add(Component.empty());
            lore.add(Component.text("패시브", NamedTextColor.DARK_AQUA));
        }
        return lore;
    }

    public static ItemStack activeItem(Skill s, int slot) {
        List<Component> lore = describe(s);
        lore.add(Component.text("우클릭으로 사용", NamedTextColor.YELLOW));
        return icon(slot == 0 ? Material.NETHER_STAR : Material.HEART_OF_THE_SEA, iconOf(s),
                Component.text("✦ " + s.name, colorOf(s)), lore, false, "active:" + slot);
    }

    public static ItemStack emptySlot(int slot) {
        return icon(slot == 0 ? Material.NETHER_STAR : Material.HEART_OF_THE_SEA, "skill_empty",
                Component.text("빈 액티브 칸", NamedTextColor.DARK_GRAY),
                List.of(Component.text("스킬트리에서 액티브 스킬을 배우면 여기에 들어와요", NamedTextColor.GRAY)), false, "active:" + slot);
    }

    public static ItemStack menuItem() {
        return icon(Material.RECOVERY_COMPASS, "skill_book", Component.text("스킬트리", NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("우클릭해서 열기", NamedTextColor.GRAY)), true, "menu");
    }
}
