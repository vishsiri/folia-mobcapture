package net.johnbrooks.mh.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PluginConfig {
    private boolean debug = false;
    private Capture capture = new Capture();
    private Cost cost = new Cost();
    private Worlds worlds = new Worlds();
    private Features features = new Features();
    private Hooks hooks = new Hooks();
    private Map<String, String> messages = defaultMessages();

    @Data
    public static class Capture {
        private boolean melee = true;
        private List<String> blacklistedEntities = new ArrayList<>(Arrays.asList("ENDER_DRAGON", "WITHER"));
        private UniqueProjectile uniqueProjectile = new UniqueProjectile();
        private CaptureItems items = new CaptureItems();
    }

    @Data
    public static class UniqueProjectile {
        private boolean enabled = false;
        private String displayName = "<gold>Mob Catcher";
        private List<String> lore = new ArrayList<>(Arrays.asList("<dark_aqua>Shoot a mob with me!"));
    }

    @Data
    public static class CaptureItems {
        private boolean enabled = false;
        private String defaultType = "basic";
        private String projectile = "EGG";
        private String consumeMode = "VANILLA";
        private Map<String, CaptureItem> types = defaultTypes();
    }

    @Data
    public static class CaptureItem {
        private String material = "EGG";
        private String displayName = "<gold>Basic Capture Egg";
        private List<String> lore = new ArrayList<>(Arrays.asList(
                "<gray>Type: <yellow>{type}",
                "<gray>Capture chance: <yellow>{chance}%",
                "<dark_gray>Right-click a mob to capture it."
        ));
        private double chance = 35D;
    }

    @Data
    public static class Cost {
        private String mode = "NONE";
        private String item = "REDSTONE";
        private int amount = 1;
        private double vault = 100D;
    }

    @Data
    public static class Worlds {
        private List<String> disabled = new ArrayList<>();
    }

    @Data
    public static class Features {
        private boolean coloredEggs = true;
        private CaptureAnimation captureAnimation = new CaptureAnimation();
        private CapturedEgg capturedEgg = new CapturedEgg();
    }

    @Data
    public static class CaptureAnimation {
        private boolean enabled = true;
        private int frames = 16;
        private int frameIntervalTicks = 2;
        private int resultStayTicks = 50;
    }

    @Data
    public static class CapturedEgg {
        private String displayName = "<gold>Captured {entity}";
        private List<String> lore = new ArrayList<>(Arrays.asList(
                "<aqua>Creature Type: <yellow>{entity_type}",
                "<aqua>Health: <yellow>{health}/{max_health}"
        ));
        /** Line added when the entity is a baby. Empty string = disabled. */
        private String loreBaby = "<aqua>Age: <yellow>Baby";
        /** Line added when the entity is tameable. {tamed} = Yes/No */
        private String loreTamed = "<aqua>Tamed: <yellow>{tamed}";
        /** Line added for Parrot variants. {color} = variant name */
        private String loreParrotColor = "<aqua>Color: <yellow>{color}";
        /** One line per active potion effect. {effect} = name, {duration} = seconds */
        private String lorePotionEffect = "<dark_purple>{effect} for {duration} seconds";
    }

    @Data
    public static class Hooks {
        private boolean towny = false;
        private boolean griefPrevention = false;
        private boolean worldGuard = false;
        private boolean releaseProtection = true;
    }

    private static Map<String, CaptureItem> defaultTypes() {
        Map<String, CaptureItem> types = new LinkedHashMap<>();
        CaptureItem basic = new CaptureItem();
        types.put("basic", basic);

        CaptureItem rare = new CaptureItem();
        rare.setDisplayName("<aqua>Rare Capture Egg");
        rare.setLore(new ArrayList<>(Arrays.asList(
                "<gray>Type: <yellow>{type}",
                "<gray>Capture chance: <yellow>{chance}%",
                "<dark_gray>Right-click a mob to capture it."
        )));
        rare.setChance(65D);
        types.put("rare", rare);

        CaptureItem master = new CaptureItem();
        master.setDisplayName("<light_purple>Master Capture Egg");
        master.setLore(new ArrayList<>(Arrays.asList(
                "<gray>Type: <yellow>{type}",
                "<gray>Capture chance: <yellow>{chance}%",
                "<dark_gray>Right-click a mob to capture it."
        )));
        master.setChance(100D);
        types.put("master", master);
        return types;
    }

    private static Map<String, String> defaultMessages() {
        Map<String, String> defaults = new LinkedHashMap<>();
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
        defaults.put("capture-item-required", "{prefix}You need a capture item to do that.");
        defaults.put("unknown-command", "{prefix}Unknown command. Use /mobcapture, /mobcapture spawn <type> [amount], /mobcapture reload, or /mobcapture version.");
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
