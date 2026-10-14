package net.johnbrooks.mh.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Message {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static Map<String, String> messages = defaultMessages();

    private Message() {
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(MINI_MESSAGE.deserialize(message));
    }

    public static Component component(String key, Object... placeholders) {
        return MINI_MESSAGE.deserialize(get(key, placeholders));
    }

    public static void sendKey(CommandSender sender, String key, Object... placeholders) {
        send(sender, get(key, placeholders));
    }

    public static String get(String key, Object... placeholders) {
        String message = messages.getOrDefault(key, key);
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            message = message.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return message;
    }

    public static void setMessages(Map<String, String> configuredMessages) {
        Map<String, String> merged = defaultMessages();
        merged.putAll(configuredMessages);
        String prefix = merged.getOrDefault("prefix", "");
        merged.replaceAll((key, value) -> value.replace("{prefix}", prefix));
        messages = Collections.unmodifiableMap(merged);
    }

    public static String legacy(String message) {
        return LEGACY_SERIALIZER.serialize(MINI_MESSAGE.deserialize(message));
    }

    public static String legacyAmpersand(String message) {
        char[] chars = message.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) >= 0) {
                chars[i] = '\u00A7';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static String colorize(String message) {
        if (message == null) {
            return "";
        }
        if (message.indexOf('&') >= 0) {
            return legacyAmpersand(message);
        }
        return legacy(message);
    }

    private static Map<String, String> defaultMessages() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("prefix", "<yellow>MobCapture:</yellow> <blue>");
        defaults.put("no-permission-command", "{prefix}You do not have permission to run this command.");
        defaults.put("info-author", "{prefix}This plugin was created by WiseHollow!");
        defaults.put("info-profile", "<blue>Profile: <click:open_url:'https://www.spigotmc.org/members/wisehollow.14804/'><underlined>https://www.spigotmc.org/members/wisehollow.14804/</underlined></click>");
        defaults.put("config-regenerated", "{prefix}Configuration has been regenerated and reloaded!");
        defaults.put("config-reloaded", "{prefix}Configuration has been reloaded!");
        defaults.put("capture-items-disabled", "{prefix}Capture item types are not enabled in the config.");
        defaults.put("player-only", "{prefix}Only players can spawn capture items.");
        defaults.put("unknown-capture-type", "{prefix}Unknown capture item type. Available: {types}");
        defaults.put("capture-item-spawned", "{prefix}Spawned {amount}x {type} capture item.");
        defaults.put("capture-item-given", "{prefix}Gave {amount}x {type} capture item to {player}.");
        defaults.put("capture-item-received", "{prefix}You received {amount}x {type} capture item from {player}.");
        defaults.put("capture-item-required", "{prefix}You need a capture item to do that.");
        defaults.put("unknown-command", "{prefix}Unknown command. Use /mobcapture, /mobcapture spawn <type> [amount], /mobcapture give <player> <type> [amount], /mobcapture reload, or /mobcapture version.");
        defaults.put("invalid-number", "{prefix}Please enter a valid number.");
        defaults.put("update-available", "{prefix}<click:open_url:'https://goo.gl/yiWQnT'><underlined>Click here</underlined></click> to get the latest version of {plugin}!");
        defaults.put("up-to-date", "{prefix}Everything is up-to-date.");
        defaults.put("version", "<blue>{plugin}'s current version: {version}");
        defaults.put("update-status", "{prefix}{status}");
        defaults.put("update-status-available", "Update is available.");
        defaults.put("update-status-current", "Everything is up-to-date.");
        defaults.put("no-permission-capture", "{prefix}You do not have permission to capture this creature.");
        defaults.put("capture-blacklisted-entity", "{prefix}You cannot capture {entity}.");
        defaults.put("no-permission-area", "{prefix}You do not have permission to capture creatures here.");
        defaults.put("capture-disabled-world", "{prefix}You cannot capture a creature in this world!");
        defaults.put("capture-failed-chance", "{prefix}The creature broke free! <gray>({chance}% chance)");
        defaults.put("capture-already-in-progress", "{prefix}A capture is already in progress.");
        defaults.put("capture-interrupted", "{prefix}The capture was interrupted before it could finish.");
        defaults.put("capture-animation-progress-title", "<yellow><bold>Capturing...</bold>");
        defaults.put("capture-animation-progress-subtitle", "<green><bold>Success</bold> <dark_gray>→ <green>{success}% <gray>| <red>{failure}% <dark_gray>← <red><bold>Failure</bold>");
        defaults.put("capture-animation-success-title", "<green><bold>Mob Captured!</bold>");
        defaults.put("capture-animation-success-subtitle", "<gray>You captured <green>{entity}</green>!");
        defaults.put("capture-animation-failure-title", "<red><bold>Capture Failed!</bold>");
        defaults.put("capture-animation-failure-subtitle", "<gray>The creature broke free. <white>({chance}% chance)");
        defaults.put("not-enough-cost", "{prefix}You do not have enough {cost}.");
        defaults.put("wallet-charged", "{prefix}[Wallet Charged: ${amount}]");
        defaults.put("item-charged", "{prefix}[{item} Charged: {amount}]");
        defaults.put("creature-released", "{prefix}{entity} successfully spawned!");
        defaults.put("release-disabled-world", "{prefix}You cannot release a creature in this world!");
        return defaults;
    }
}
