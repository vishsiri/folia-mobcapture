package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.debug.DebugLog;
import net.johnbrooks.mh.domain.item.CaptureItemConsumeMode;
import net.johnbrooks.mh.domain.item.CaptureItemType;
import net.johnbrooks.mh.domain.item.UniqueProjectileData;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectileService {
    private final Map<UUID, CaptureItemType> pendingLaunches = new ConcurrentHashMap<>();
    private final NamespacedKey captureProjectileKey = new NamespacedKey(Main.plugin, "capture_projectile");
    private final NamespacedKey captureChanceKey = new NamespacedKey(Main.plugin, "capture_chance");

    public void prepareCaptureProjectile(Player player, ItemStack itemStack) {
        if (!isCaptureItem(itemStack)) {
            return;
        }

        if (UniqueProjectileData.isEnabled()) {
            UniqueProjectileData.findType(itemStack).ifPresent(type -> {
                UUID playerId = player.getUniqueId();
                pendingLaunches.put(playerId, type);
                if (DebugLog.isEnabled()) {
                    DebugLog.info("Prepared capture projectile: player=" + player.getName()
                            + ", type=" + type.getId() + ", material=" + type.getMaterial().name() + ".");
                }
                player.getScheduler().runDelayed(
                        Main.plugin,
                        scheduledTask -> pendingLaunches.remove(playerId, type),
                        () -> pendingLaunches.remove(playerId, type),
                        1L
                );
            });
        }
    }

    public void markIfCaptureProjectile(Projectile projectile) {
        if (!(projectile.getShooter() instanceof Player) || !matchesConfiguredProjectile(projectile)) {
            return;
        }

        Player shooter = (Player) projectile.getShooter();
        double chance = 100D;
        String captureTypeId = "default";
        if (UniqueProjectileData.isEnabled()) {
            CaptureItemType type = pendingLaunches.remove(shooter.getUniqueId());
            if (type == null || !materialMatchesProjectile(projectile, type.getMaterial())) {
                type = findCaptureItem(shooter).orElse(null);
            }
            if (type == null || !materialMatchesProjectile(projectile, type.getMaterial())) {
                if (DebugLog.isEnabled()) {
                    DebugLog.info("Projectile ignored: no matching capture item was found (player="
                            + shooter.getName() + ", projectile=" + projectile.getType().name() + ").");
                }
                return;
            }
            chance = type.getChance();
            captureTypeId = type.getId();
            refundThrownCaptureItemIfNeeded(shooter, type);
        }

        projectile.getPersistentDataContainer().set(captureProjectileKey, PersistentDataType.BYTE, (byte) 1);
        projectile.getPersistentDataContainer().set(captureChanceKey, PersistentDataType.DOUBLE, chance);
        if (DebugLog.isEnabled()) {
            DebugLog.info("Marked capture projectile: player=" + shooter.getName()
                    + ", type=" + captureTypeId
                    + ", projectile=" + projectile.getType().name()
                    + ", chance=" + chance + "%.");
        }
    }

    public boolean isCaptureProjectile(Projectile projectile) {
        return projectile.getPersistentDataContainer().has(captureProjectileKey, PersistentDataType.BYTE);
    }

    public double getCaptureChance(Projectile projectile) {
        return projectile.getPersistentDataContainer().getOrDefault(
                captureChanceKey,
                PersistentDataType.DOUBLE,
                100D
        );
    }

    public double getCaptureChance(ItemStack itemStack) {
        if (!UniqueProjectileData.isEnabled()) {
            return 100D;
        }
        return UniqueProjectileData.findType(itemStack).map(CaptureItemType::getChance).orElse(0D);
    }

    private boolean matchesConfiguredProjectile(Projectile projectile) {
        return projectile.getType().name().equalsIgnoreCase(Settings.projectileCatcherMaterial.name())
                || UniqueProjectileData.isCaptureMaterialName(projectile.getType().name());
    }

    private Optional<CaptureItemType> findCaptureItem(Player player) {
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            Optional<CaptureItemType> captureType = UniqueProjectileData.findType(itemStack);
            if (captureType.isPresent()) {
                return captureType;
            }
        }
        return Optional.empty();
    }

    public boolean isCaptureItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (UniqueProjectileData.isEnabled()) {
            return UniqueProjectileData.isProjectile(itemStack);
        }
        return itemStack.getType() == Settings.projectileCatcherMaterial;
    }

    public boolean consumeCaptureItem(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return true;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (consumeOne(mainHand)) {
            player.getInventory().setItemInMainHand(remainingItem(mainHand));
            return true;
        }

        int heldSlot = player.getInventory().getHeldItemSlot();
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot == heldSlot) {
                continue;
            }
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (consumeOne(itemStack)) {
                player.getInventory().setItem(slot, remainingItem(itemStack));
                return true;
            }
        }
        if (DebugLog.isEnabled()) {
            DebugLog.info("Capture item consumption failed: no configured capture item found for player="
                    + player.getName() + ".");
        }
        return false;
    }

    private boolean materialMatchesProjectile(Projectile projectile, org.bukkit.Material material) {
        return projectile.getType().name().equalsIgnoreCase(material.name());
    }

    private void refundThrownCaptureItemIfNeeded(Player player, CaptureItemType type) {
        if (Settings.captureItemConsumeMode != CaptureItemConsumeMode.NEVER
                && Settings.captureItemConsumeMode != CaptureItemConsumeMode.ON_SUCCESS) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(UniqueProjectileData.spawn(type.getId(), 1));
        overflow.values().forEach(itemStack -> player.getWorld().dropItemNaturally(player.getLocation(), itemStack));
        if (DebugLog.isEnabled()) {
            DebugLog.info("Refunded thrown capture item: player=" + player.getName()
                    + ", type=" + type.getId() + ", overflow=" + overflow.size() + ".");
        }
    }

    private boolean consumeOne(ItemStack itemStack) {
        if (!isCaptureItem(itemStack)) {
            return false;
        }
        itemStack.setAmount(itemStack.getAmount() - 1);
        return true;
    }

    private ItemStack remainingItem(ItemStack itemStack) {
        return itemStack.getAmount() <= 0 ? new ItemStack(Material.AIR) : itemStack;
    }
}
