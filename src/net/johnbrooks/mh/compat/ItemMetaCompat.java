package net.johnbrooks.mh.compat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemMetaCompat {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private ItemMetaCompat() {
    }

    /**
     * Parses a string as MiniMessage, legacy §-codes, or legacy &-codes — whichever applies.
     * This lets config authors use any format and get proper Adventure Components.
     */
    private static Component parseComponent(String text) {
        if (text == null) return Component.empty();
        Component component;
        if (text.indexOf('\u00A7') >= 0) {    // § already present
            component = LEGACY_SECTION.deserialize(text);
        } else if (text.indexOf('&') >= 0) {  // & color codes
            component = LEGACY_AMPERSAND.deserialize(text);
        } else {
            component = MINI_MESSAGE.deserialize(text); // MiniMessage tags
        }
        // Minecraft defaults custom names/lore to italic — suppress it unless
        // the config string explicitly requests italic with <italic> or §o.
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void setDisplayName(ItemMeta meta, String displayName) {
        meta.displayName(parseComponent(displayName));
    }

    public static void setLore(ItemMeta meta, List<String> lore) {
        List<Component> components = new ArrayList<>(lore.size());
        for (String line : lore) {
            components.add(parseComponent(line));
        }
        meta.lore(components);
    }

    public static String getDisplayName(ItemMeta meta) {
        Component displayName = meta.displayName();
        if (displayName != null) {
            return LEGACY_SECTION.serialize(displayName);
        }
        return "";
    }
}
