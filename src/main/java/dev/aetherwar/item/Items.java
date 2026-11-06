package dev.aetherwar.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** Custom items. Tagged with PDC so renamed vanilla items can't pass as crystals. */
public final class Items {
    public static final String ETHER_CRYSTAL = "ether_crystal";
    private static NamespacedKey key;

    private Items() {}

    public static void init(Plugin plugin) {
        key = new NamespacedKey(plugin, "item");
    }

    public static ItemStack crystal(int amount) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD, amount);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text("에테르 결정", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("자기 팀 코어 근처로 가져가면 납품된다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("죽으면 들고 있던 결정의 절반을 떨군다", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, ETHER_CRYSTAL);
        var cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(ETHER_CRYSTAL)); // resource pack swaps in the crystal model
        meta.setCustomModelDataComponent(cmd);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isCrystal(ItemStack it) {
        if (it == null || it.getType() != Material.AMETHYST_SHARD || !it.hasItemMeta()) return false;
        return ETHER_CRYSTAL.equals(it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }

    public static int countCrystals(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isCrystal(it)) n += it.getAmount();
        return n;
    }

    /** Removes up to {@code amount} crystals; returns how many were removed. */
    public static int takeCrystals(Player p, int amount) {
        int left = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!isCrystal(it)) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
        }
        return amount - left;
    }
}
