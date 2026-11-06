package dev.aetherwar.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** A chest GUI. Titles draw a background picture from the resource pack (font glyph trick). */
public abstract class Menu implements InventoryHolder {
    public static final Key GUI_FONT = Key.key("aetherwar", "gui");
    /** Glyphs in the aetherwar:gui font. */
    public static final String BG_TREE = "\uE100", BG_SMALL = "\uE101", BG_AUGMENT = "\uE102";
    /** Negative spaces: -8 and -169 / -177 pixels (see build_resourcepack.py). */
    public static final String NEG_8 = "", BACK_FROM_BG = "";

    protected Inventory inv;
    protected final Player viewer;

    protected Menu(Player viewer) {
        this.viewer = viewer;
    }

    /** Title with the background image, then the text in its usual place. */
    protected static Component title(String bg, String text, TextColor color) {
        return Component.text(NEG_8 + bg + BACK_FROM_BG).font(GUI_FONT).color(NamedTextColor.WHITE)
                .append(Component.text(text, color).font(Key.key("minecraft", "default")));
    }

    protected Inventory create(int size, Component title) {
        inv = Bukkit.createInventory(this, size, title);
        return inv;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        build();
        viewer.openInventory(inv);
    }

    /** Re-fills the items without reopening. */
    public abstract void build();

    public abstract void click(int slot, ClickType type);
}
