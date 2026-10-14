package net.johnbrooks.mh.config;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.compat.TownyHook;
import net.johnbrooks.mh.compat.WorldGuardHook;
import net.johnbrooks.mh.domain.item.CaptureItemConsumeMode;
import net.johnbrooks.mh.domain.item.CaptureItemType;
import net.johnbrooks.mh.domain.item.UniqueProjectileData;
import net.johnbrooks.mh.i18n.Message;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Settings {
    public enum CostMode { NONE, VAULT, ITEM }

    public static boolean debugMode = false;
    public static CostMode costMode = CostMode.NONE;

    public static Material projectileCatcherMaterial = Material.SNOWBALL;

    public static Material costMaterial = Material.REDSTONE;
    public static int costAmount = 1;
    public static double costVault = 100;

    public static boolean coloredEggs = true;
    public static PluginConfig.CapturedEgg capturedEgg = new PluginConfig.CapturedEgg();
    public static boolean captureAnimationEnabled = true;
    public static int captureAnimationFrames = 16;
    public static int captureAnimationFrameIntervalTicks = 2;
    public static int captureAnimationResultStayTicks = 50;

    private static Set<String> disabledWorlds = Set.of();

    public static boolean townyHook = false;
    public static boolean griefPreventionHook = false;
    public static boolean worldGuardHook = false;
    public static boolean releaseProtection = true;
    public static boolean meleeCapture = true;
    public static CaptureItemConsumeMode captureItemConsumeMode = CaptureItemConsumeMode.VANILLA;
    private static Set<EntityType> blacklistedEntities = Set.of();

    public static void load() {
        PluginConfig config = ConfigManager.load();
        debugMode = config.isDebug();

        try {
            Material configuredProjectile = Material.getMaterial(config.getCapture().getItems().getProjectile());
            Material configuredCost = Material.getMaterial(config.getCost().getItem());
            if (configuredProjectile == null || configuredCost == null) {
                throw new IllegalArgumentException("Invalid configured material");
            }
            projectileCatcherMaterial = configuredProjectile;
            costMaterial = configuredCost;
        } catch(Exception ex) {
            Main.logger.severe("Invalid material name! Check " + Main.plugin.getName() + "'s " +
                    "SpigotMC page to get Material names.");
        }

        try
        {
            costMode = CostMode.valueOf(config.getCost().getMode().toUpperCase());
        } catch(Exception ex) {
            Main.logger.severe("Invalid Cost Mode! Use either 'NONE', 'VAULT', or 'ITEM'.");
        }

        // 3) If vault is set, hook into vault.
        if (costMode == CostMode.VAULT) {
            Main.logger.info("Vault hook " + (setupEconomy() ? "was successful!" : "has failed!"));
            if (Main.economy == null)
            {
                Main.logger.warning("Reverting cost mode to ITEM.");
                costMode = CostMode.ITEM;
            }
        } else if (costMode == CostMode.ITEM)
            Main.logger.info("Cost Mode is ITEM, skipping Vault hook.");
        else
            Main.logger.info("Cost Mode is NONE, skipping hooks.");

        costAmount = config.getCost().getAmount();
        if (costAmount < 0) {
            Main.logger.warning("Invalid negative item capture cost. Using 0 instead.");
            costAmount = 0;
        }
        costVault = config.getCost().getVault();
        if (!Double.isFinite(costVault) || costVault < 0) {
            Main.logger.warning("Invalid negative or non-finite Vault capture cost. Using 0 instead.");
            costVault = 0;
        }
        disabledWorlds = readDisabledWorlds(config.getWorlds().getDisabled());
        coloredEggs = config.getFeatures().isColoredEggs();
        capturedEgg = config.getFeatures().getCapturedEgg();
        PluginConfig.CaptureAnimation captureAnimation = config.getFeatures().getCaptureAnimation();
        captureAnimationEnabled = captureAnimation.isEnabled();
        captureAnimationFrames = boundedAnimationValue("frames", captureAnimation.getFrames(), 2, 40);
        captureAnimationFrameIntervalTicks = boundedAnimationValue(
                "frame-interval-ticks", captureAnimation.getFrameIntervalTicks(), 1, 20);
        captureAnimationResultStayTicks = boundedAnimationValue(
                "result-stay-ticks", captureAnimation.getResultStayTicks(), 1, 200);
        townyHook = config.getHooks().isTowny();
        griefPreventionHook = config.getHooks().isGriefPrevention();
        worldGuardHook = config.getHooks().isWorldGuard();
        releaseProtection = config.getHooks().isReleaseProtection();
        meleeCapture = config.getCapture().isMelee();
        blacklistedEntities = readBlacklistedEntities(config.getCapture().getBlacklistedEntities());
        captureItemConsumeMode = readConsumeMode(config.getCapture().getItems().getConsumeMode());

        if (townyHook) {
            TownyHook.refresh();
            Main.logger.info("Towny hook " + (TownyHook.isAvailable() ? "was successful!" : "has failed!"));
        }
        if (griefPreventionHook) {
            Main.griefPrevention = GriefPrevention.instance;
            Main.logger.info("GriefPrevention hook " + (Main.griefPrevention != null
                    ? "was successful!" : "has failed!"));
        }
        if (worldGuardHook) {
            WorldGuardHook.refresh();
            Main.logger.info("WorldGuard hook " + (WorldGuardHook.isAvailable() ? "was successful!" : "has failed!"));
        }
        if (!releaseProtection && (townyHook || griefPreventionHook || worldGuardHook)) {
            Main.logger.info("Hook protection checks are disabled for captured mob releases.");
        }

        UniqueProjectileData.setEnabled(config.getCapture().getItems().isEnabled());
        UniqueProjectileData.setDefaultType(config.getCapture().getItems().getDefaultType());
        UniqueProjectileData.setTypes(readCaptureItemTypes(config));
        Message.setMessages(config.getMessages());

        if (debugMode) {
            Main.logger.info("[Debug] Debug mode enabled. Capture and release decisions will be logged.");
            Main.logger.info("[Debug] Hooks: towny=" + townyHook
                    + ", grief-prevention=" + griefPreventionHook
                    + ", worldguard=" + worldGuardHook
                    + ", release-protection=" + releaseProtection);
        }
    }

    public static boolean isDisabledWorld(String worldName) {
        return worldName != null && disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public static boolean isBlacklistedEntity(LivingEntity livingEntity) {
        return blacklistedEntities.contains(livingEntity.getType());
    }

    private static boolean setupEconomy() {
        RegisteredServiceProvider<Economy> economyProvider = Main.plugin.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            Main.economy = economyProvider.getProvider();
        }
        return (Main.economy != null);
    }

    private static List<CaptureItemType> readCaptureItemTypes(PluginConfig config) {
        List<CaptureItemType> captureTypes = new ArrayList<>();
        for (Map.Entry<String, PluginConfig.CaptureItem> entry : config.getCapture().getItems().getTypes().entrySet()) {
            PluginConfig.CaptureItem item = entry.getValue();
            Material material = Material.getMaterial(item.getMaterial().toUpperCase());
            if (material == null) {
                Main.logger.warning("Invalid capture item material for type '" + entry.getKey() + "': " + item.getMaterial());
                continue;
            }
            captureTypes.add(new CaptureItemType(entry.getKey(), material, item.getDisplayName(), item.getLore(), item.getChance()));
        }
        return captureTypes;
    }

    private static Set<String> readDisabledWorlds(List<String> configuredWorlds) {
        Set<String> worlds = new HashSet<>();
        for (String world : configuredWorlds) {
            if (world != null && !world.isBlank()) {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(worlds);
    }

    private static Set<EntityType> readBlacklistedEntities(List<String> configuredEntities) {
        Set<String> configuredNames = new HashSet<>();
        for (String entity : configuredEntities) {
            if (entity != null && !entity.trim().isEmpty()) {
                configuredNames.add(normalizeEntityName(entity));
            }
        }

        Set<EntityType> entities = new HashSet<>();
        for (EntityType entityType : EntityType.values()) {
            if (configuredNames.contains(normalizeEntityName(entityType.name()))) {
                entities.add(entityType);
            }
        }
        return Set.copyOf(entities);
    }

    private static String normalizeEntityName(String entity) {
        return entity.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static CaptureItemConsumeMode readConsumeMode(String value) {
        try {
            return CaptureItemConsumeMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            Main.logger.warning("Invalid capture item consume mode '" + value + "'. Using VANILLA.");
            return CaptureItemConsumeMode.VANILLA;
        }
    }

    private static int boundedAnimationValue(String name, int value, int minimum, int maximum) {
        int bounded = Math.max(minimum, Math.min(maximum, value));
        if (bounded != value) {
            Main.logger.warning("Invalid capture animation " + name + " value '" + value
                    + "'. Using " + bounded + " instead.");
        }
        return bounded;
    }
}
