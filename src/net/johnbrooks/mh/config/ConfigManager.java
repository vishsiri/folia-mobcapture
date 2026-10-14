package net.johnbrooks.mh.config;

import net.johnbrooks.mh.Main;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {
    private ConfigManager() {
    }

    public static PluginConfig load() {
        Path configPath = Main.plugin.getDataFolder().toPath().resolve("config.yml");
        if (!configPath.toFile().exists()) {
            Main.plugin.saveResource("config.yml", false);
        }

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configPath)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build();
            CommentedConfigurationNode root = loader.load();
            PluginConfig config = read(root);
            if (migrate(root, config)) {
                loader.save(root);
                Main.logger.info("config.yml has been migrated with missing default options.");
            }
            return config;
        } catch (ConfigurateException ex) {
            Main.logger.warning("Unable to load config.yml, using defaults: " + ex.getMessage());
            return new PluginConfig();
        }
    }

    private static PluginConfig read(CommentedConfigurationNode root) throws SerializationException {
        PluginConfig config = new PluginConfig();

        config.setDebug(bool(root, false, "Debug", "debug"));
        config.getCapture().setMelee(bool(root, true, "MeleeCapture", "capture", "melee"));
        config.getCapture().setBlacklistedEntities(stringList(root, config.getCapture().getBlacklistedEntities(), "", "capture", "blacklisted-entities"));
        config.getCapture().getUniqueProjectile().setEnabled(bool(root, false, "Require Unique", "capture", "unique-projectile", "enabled"));
        config.getCapture().getUniqueProjectile().setDisplayName(string(root, "<gold>Mob Catcher", "Unique Display Name", "capture", "unique-projectile", "display-name"));
        config.getCapture().getUniqueProjectile().setLore(stringList(root, config.getCapture().getUniqueProjectile().getLore(), "Unique Lore", "capture", "unique-projectile", "lore"));
        config.getCapture().getItems().setEnabled(bool(root, config.getCapture().getUniqueProjectile().isEnabled(), "Require Unique", "capture", "items", "enabled"));
        config.getCapture().getItems().setDefaultType(string(root, "basic", "Unique Type", "capture", "items", "default-type"));
        // Read projectile from new path (capture.items.projectile), fall back to old path (capture.projectile)
        String projectile = root.node("capture", "items", "projectile").getString();
        if (projectile == null) {
            projectile = root.node("capture", "projectile").getString("EGG");
        }
        config.getCapture().getItems().setProjectile(projectile);
        config.getCapture().getItems().setConsumeMode(string(root, "VANILLA", "Unique Consume Mode", "capture", "items", "consume-mode"));
        config.getCapture().getItems().setTypes(captureItems(root, config));

        config.getCost().setMode(string(root, "NONE", "Cost Mode", "cost", "mode"));
        config.getCost().setItem(string(root, "REDSTONE", "Cost Item", "cost", "item"));
        config.getCost().setAmount(integer(root, 1, "Cost Amount", "cost", "amount"));
        config.getCost().setVault(decimal(root, 100D, "Cost Vault", "cost", "vault"));

        config.getWorlds().setDisabled(stringList(root, config.getWorlds().getDisabled(), "Disabled Worlds", "worlds", "disabled"));
        config.getFeatures().setColoredEggs(bool(root, true, "Colored Eggs", "features", "colored-eggs"));

        PluginConfig.CaptureAnimation captureAnimation = config.getFeatures().getCaptureAnimation();
        captureAnimation.setEnabled(bool(root, captureAnimation.isEnabled(), "", "features", "capture-animation", "enabled"));
        captureAnimation.setFrames(integer(root, captureAnimation.getFrames(), "", "features", "capture-animation", "frames"));
        captureAnimation.setFrameIntervalTicks(integer(root, captureAnimation.getFrameIntervalTicks(), "", "features", "capture-animation", "frame-interval-ticks"));
        captureAnimation.setResultStayTicks(integer(root, captureAnimation.getResultStayTicks(), "", "features", "capture-animation", "result-stay-ticks"));

        PluginConfig.CapturedEgg capturedEgg = config.getFeatures().getCapturedEgg();
        capturedEgg.setDisplayName(string(root, capturedEgg.getDisplayName(), "", "features", "captured-egg", "display-name"));
        capturedEgg.setLore(stringList(root, capturedEgg.getLore(), "", "features", "captured-egg", "lore"));
        capturedEgg.setLoreBaby(string(root, capturedEgg.getLoreBaby(), "", "features", "captured-egg", "lore-baby"));
        capturedEgg.setLoreTamed(string(root, capturedEgg.getLoreTamed(), "", "features", "captured-egg", "lore-tamed"));
        capturedEgg.setLoreParrotColor(string(root, capturedEgg.getLoreParrotColor(), "", "features", "captured-egg", "lore-parrot-color"));
        capturedEgg.setLorePotionEffect(string(root, capturedEgg.getLorePotionEffect(), "", "features", "captured-egg", "lore-potion-effect"));

        config.getHooks().setTowny(bool(root, false, "Towny Hook", "hooks", "towny"));
        config.getHooks().setGriefPrevention(bool(root, false, "GriefPrevention Hook", "hooks", "grief-prevention"));
        config.getHooks().setWorldGuard(bool(root, false, "WorldGuard Hook", "hooks", "worldguard"));
        config.getHooks().setReleaseProtection(bool(root, true, "", "hooks", "release-protection"));
        config.setMessages(messages(root, config.getMessages()));

        return config;
    }

    private static boolean migrate(CommentedConfigurationNode root, PluginConfig config) throws SerializationException {
        boolean changed = false;
        if (root.childrenMap().containsKey("")) {
            root.removeChild("");
            changed = true;
        }

        changed |= repairEmptyBlacklistedEntities(root);
        changed |= setIfMissing(root, config.isDebug(), "debug");
        changed |= setIfMissing(root, config.getCapture().isMelee(), "capture", "melee");
        changed |= setIfMissing(root, config.getCapture().getBlacklistedEntities(), "capture", "blacklisted-entities");

        PluginConfig.CaptureItems items = config.getCapture().getItems();
        changed |= setIfMissing(root, items.isEnabled(), "capture", "items", "enabled");
        changed |= setIfMissing(root, items.getDefaultType(), "capture", "items", "default-type");
        changed |= setIfMissing(root, items.getProjectile(), "capture", "items", "projectile");
        changed |= setIfMissing(root, items.getConsumeMode(), "capture", "items", "consume-mode");
        for (Map.Entry<String, PluginConfig.CaptureItem> entry : items.getTypes().entrySet()) {
            PluginConfig.CaptureItem item = entry.getValue();
            changed |= setIfMissing(root, item.getMaterial(), "capture", "items", "types", entry.getKey(), "material");
            changed |= setIfMissing(root, item.getChance(), "capture", "items", "types", entry.getKey(), "chance");
            changed |= setIfMissing(root, item.getDisplayName(), "capture", "items", "types", entry.getKey(), "display-name");
            changed |= setIfMissing(root, item.getLore(), "capture", "items", "types", entry.getKey(), "lore");
        }

        changed |= setIfMissing(root, config.getCost().getMode(), "cost", "mode");
        changed |= setIfMissing(root, config.getCost().getItem(), "cost", "item");
        changed |= setIfMissing(root, config.getCost().getAmount(), "cost", "amount");
        changed |= setIfMissing(root, config.getCost().getVault(), "cost", "vault");

        changed |= setIfMissing(root, config.getWorlds().getDisabled(), "worlds", "disabled");
        changed |= setIfMissing(root, config.getFeatures().isColoredEggs(), "features", "colored-eggs");

        PluginConfig.CaptureAnimation captureAnimation = config.getFeatures().getCaptureAnimation();
        changed |= setIfMissing(root, captureAnimation.isEnabled(), "features", "capture-animation", "enabled");
        changed |= setIfMissing(root, captureAnimation.getFrames(), "features", "capture-animation", "frames");
        changed |= setIfMissing(root, captureAnimation.getFrameIntervalTicks(), "features", "capture-animation", "frame-interval-ticks");
        changed |= setIfMissing(root, captureAnimation.getResultStayTicks(), "features", "capture-animation", "result-stay-ticks");

        PluginConfig.CapturedEgg capturedEgg = config.getFeatures().getCapturedEgg();
        changed |= setIfMissing(root, capturedEgg.getDisplayName(), "features", "captured-egg", "display-name");
        changed |= setIfMissing(root, capturedEgg.getLore(), "features", "captured-egg", "lore");
        changed |= setIfMissing(root, capturedEgg.getLoreBaby(), "features", "captured-egg", "lore-baby");
        changed |= setIfMissing(root, capturedEgg.getLoreTamed(), "features", "captured-egg", "lore-tamed");
        changed |= setIfMissing(root, capturedEgg.getLoreParrotColor(), "features", "captured-egg", "lore-parrot-color");
        changed |= setIfMissing(root, capturedEgg.getLorePotionEffect(), "features", "captured-egg", "lore-potion-effect");

        changed |= setIfMissing(root, config.getHooks().isTowny(), "hooks", "towny");
        changed |= setIfMissing(root, config.getHooks().isGriefPrevention(), "hooks", "grief-prevention");
        changed |= setIfMissing(root, config.getHooks().isWorldGuard(), "hooks", "worldguard");
        changed |= setIfMissing(root, config.getHooks().isReleaseProtection(), "hooks", "release-protection");

        for (Map.Entry<String, String> entry : config.getMessages().entrySet()) {
            changed |= setIfMissing(root, entry.getValue(), "messages", entry.getKey());
        }

        return changed;
    }

    private static boolean repairEmptyBlacklistedEntities(CommentedConfigurationNode root) throws SerializationException {
        CommentedConfigurationNode node = root.node("capture", "blacklisted-entities");
        if (node.virtual()) {
            return false;
        }

        List<String> configured = node.getList(String.class);
        if (configured != null && !configured.isEmpty()) {
            return false;
        }

        node.set(new PluginConfig.Capture().getBlacklistedEntities());
        return true;
    }

    private static boolean setIfMissing(CommentedConfigurationNode root, Object value, Object... path) throws SerializationException {
        CommentedConfigurationNode node = root.node(path);
        if (!node.virtual()) {
            return false;
        }
        node.set(value);
        return true;
    }

    private static String string(CommentedConfigurationNode root, String fallback, String legacyKey, Object... path) {
        String value = root.node(path).getString();
        if (value != null) {
            return value;
        }
        if (legacyKey == null || legacyKey.isEmpty()) {
            return fallback;
        }
        value = root.node(legacyKey).getString();
        return value == null ? fallback : value;
    }

    private static boolean bool(CommentedConfigurationNode root, boolean fallback, String legacyKey, Object... path) {
        if (!root.node(path).virtual()) {
            return root.node(path).getBoolean(fallback);
        }
        if (legacyKey == null || legacyKey.isEmpty()) {
            return fallback;
        }
        return root.node(legacyKey).getBoolean(fallback);
    }

    private static int integer(CommentedConfigurationNode root, int fallback, String legacyKey, Object... path) {
        if (!root.node(path).virtual()) {
            return root.node(path).getInt(fallback);
        }
        if (legacyKey == null || legacyKey.isEmpty()) {
            return fallback;
        }
        return root.node(legacyKey).getInt(fallback);
    }

    private static double decimal(CommentedConfigurationNode root, double fallback, String legacyKey, Object... path) {
        if (!root.node(path).virtual()) {
            return root.node(path).getDouble(fallback);
        }
        if (legacyKey == null || legacyKey.isEmpty()) {
            return fallback;
        }
        return root.node(legacyKey).getDouble(fallback);
    }

    private static List<String> stringList(CommentedConfigurationNode root, List<String> fallback, String legacyKey, Object... path) throws SerializationException {
        if (!root.node(path).virtual()) {
            List<String> value = root.node(path).getList(String.class);
            return value == null ? fallback : value;
        }

        if (legacyKey == null || legacyKey.isEmpty()) {
            return new ArrayList<>(fallback);
        }
        List<String> legacyValue = root.node(legacyKey).getList(String.class);
        return legacyValue == null ? new ArrayList<>(fallback) : legacyValue;
    }

    private static Map<String, PluginConfig.CaptureItem> captureItems(CommentedConfigurationNode root, PluginConfig config) throws SerializationException {
        CommentedConfigurationNode typesNode = root.node("capture", "items", "types");
        if (typesNode.virtual() || typesNode.childrenMap().isEmpty()) {
            if (!root.node("capture", "unique-projectile").virtual() || !root.node("Unique Display Name").virtual()) {
                Map<String, PluginConfig.CaptureItem> legacyTypes = new LinkedHashMap<>();
                PluginConfig.CaptureItem legacy = new PluginConfig.CaptureItem();
                legacy.setMaterial(config.getCapture().getItems().getProjectile());
                legacy.setDisplayName(config.getCapture().getUniqueProjectile().getDisplayName());
                legacy.setLore(config.getCapture().getUniqueProjectile().getLore());
                legacy.setChance(100D);
                legacyTypes.put(config.getCapture().getItems().getDefaultType(), legacy);
                return legacyTypes;
            }
            return config.getCapture().getItems().getTypes();
        }

        Map<String, PluginConfig.CaptureItem> types = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : typesNode.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            CommentedConfigurationNode itemNode = entry.getValue();
            PluginConfig.CaptureItem item = new PluginConfig.CaptureItem();
            item.setMaterial(itemNode.node("material").getString(config.getCapture().getItems().getProjectile()));
            item.setDisplayName(itemNode.node("display-name").getString(item.getDisplayName()));
            List<String> lore = itemNode.node("lore").getList(String.class);
            item.setLore(lore == null ? item.getLore() : lore);
            item.setChance(itemNode.node("chance").getDouble(item.getChance()));
            types.put(id, item);
        }
        return types;
    }

    private static Map<String, String> messages(CommentedConfigurationNode root, Map<String, String> fallback) {
        Map<String, String> messages = new LinkedHashMap<>(fallback);
        CommentedConfigurationNode messagesNode = root.node("messages");
        if (messagesNode.virtual()) {
            return messages;
        }

        for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : messagesNode.childrenMap().entrySet()) {
            String value = entry.getValue().getString();
            if (value != null) {
                messages.put(String.valueOf(entry.getKey()), value);
            }
        }
        return messages;
    }
}
