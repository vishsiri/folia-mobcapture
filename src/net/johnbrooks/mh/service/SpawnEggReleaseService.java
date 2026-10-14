package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.i18n.Message;
import net.johnbrooks.mh.compat.WorldGuardHook;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.debug.DebugLog;
import net.johnbrooks.mh.event.custom.CreatureReleaseEvent;
import net.johnbrooks.mh.domain.item.CaptureEgg;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class SpawnEggReleaseService {
    private final PluginEventBus eventBus;

    public SpawnEggReleaseService(PluginEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void releaseAtBlock(Player player, ItemStack spawnItem, Location clickedBlock, BlockFace blockFace) {
        Location target = releaseLocation(player, clickedBlock, blockFace);
        if (DebugLog.isEnabled()) {
            DebugLog.info("Release attempt: player=" + player.getName()
                    + ", item=" + spawnItem.getType().name()
                    + ", location=" + DebugLog.location(target) + ".");
        }
        if (!canUseProtectedArea(player, target.clone().add(0, 1, 0))) {
            return;
        }

        CreatureReleaseEvent releaseEvent = new CreatureReleaseEvent(player, target);
        eventBus.call(releaseEvent);
        if (releaseEvent.isCancelled()) {
            DebugLog.info("Release denied: CreatureReleaseEvent was cancelled by a listener.");
            return;
        }

        LivingEntity spawnedEntity = CaptureEgg.useSpawnItem(spawnItem, target);
        if (spawnedEntity == null) {
            DebugLog.info("Release failed: captured entity data could not be restored.");
            return;
        }
        consumeSpawnEgg(player);
        if (DebugLog.isEnabled()) {
            DebugLog.info("Release succeeded: player=" + player.getName()
                    + ", entity=" + spawnedEntity.getType().name()
                    + ", uuid=" + spawnedEntity.getUniqueId() + ".");
        }
        Message.sendKey(player, "creature-released", "entity", spawnedEntity.getType().name());
    }

    public boolean canReleaseInWorld(Player player) {
        if (!Settings.isDisabledWorld(player.getWorld().getName())) {
            return true;
        }

        if (DebugLog.isEnabled()) {
            DebugLog.info("Release denied: world is disabled (world=" + player.getWorld().getName() + ").");
        }
        Message.sendKey(player, "release-disabled-world");
        return false;
    }

    private boolean canUseProtectedArea(Player player, Location location) {
        if (!Settings.releaseProtection) {
            DebugLog.info("Release protection checks skipped by hooks.release-protection=false.");
            return true;
        }

        if (Settings.griefPreventionHook) {
            if (Main.griefPrevention == null) {
                DebugLog.info("Release denied by GriefPrevention hook: plugin instance is unavailable.");
                Message.sendKey(player, "no-permission-area");
                return false;
            }
            boolean claimsEnabled = Main.griefPrevention.claimsEnabledForWorld(location.getWorld());
            boolean allowed = !claimsEnabled || Main.griefPrevention.allowBuild(player, location) == null;
            if (DebugLog.isEnabled()) {
                DebugLog.info("GriefPrevention release check: player=" + player.getName()
                        + ", claims-enabled=" + claimsEnabled + ", allowed=" + allowed + ".");
            }
            if (!allowed) {
                Message.sendKey(player, "no-permission-area");
                return false;
            }
        }

        if (Settings.worldGuardHook) {
            boolean canBuild = WorldGuardHook.canBuild(player, location);
            boolean canSpawnMobs = canBuild && WorldGuardHook.canSpawnMobs(player, location);
            if (DebugLog.isEnabled()) {
                DebugLog.info("WorldGuard release check: player=" + player.getName()
                        + ", build=" + canBuild + ", mob-spawning=" + canSpawnMobs
                        + ", location=" + DebugLog.location(location) + ".");
            }
            if (!canBuild || !canSpawnMobs) {
                Message.sendKey(player, "no-permission-area");
                return false;
            }
        }

        return true;
    }

    private Location releaseLocation(Player player, Location clickedBlock, BlockFace blockFace) {
        Location target = clickedBlock.clone().add(0.5, 0, 0.5);
        if (blockFace == BlockFace.UP) {
            return target;
        }

        Vector direction = player.getLocation().toVector().subtract(target.toVector()).normalize();
        return target.add(direction.multiply(2));
    }

    private void consumeSpawnEgg(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }

        heldItem.setAmount(heldItem.getAmount() - 1);
    }
}
