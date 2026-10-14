package net.johnbrooks.mh.domain.item;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.debug.DebugLog;
import net.johnbrooks.mh.persistence.NBTManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.logging.Level;

public class CaptureEgg {

    public static boolean captureLivingEntity(LivingEntity livingEntity) {
        Item drop = null;
        try {
            ItemStack eggItemStack = CaptureEgg.get(livingEntity);
            if (eggItemStack == null) {
                return false;
            }
            drop = livingEntity.getLocation().getWorld().dropItem(livingEntity.getLocation(), eggItemStack);
            drop.setVelocity(new Vector(0, 0.3f, 0));
            return true;
        } catch (RuntimeException ex) {
            if (drop != null && drop.isValid()) {
                drop.remove();
            }
            if (DebugLog.isEnabled()) {
                Main.logger.log(Level.WARNING, "Unable to create the captured entity item.", ex);
            } else {
                Main.logger.warning("Unable to create the captured entity item: " + ex.getMessage());
            }
            return false;
        }
    }

    public static LivingEntity useSpawnItem(ItemStack spawnItem, Location target) {
        return NBTManager.spawnEntityFromNBTData(spawnItem, target);
    }

    private static ItemStack get(LivingEntity livingEntity) {
        return convertEntityToEntitySpawnEgg(livingEntity);
    }

    private static ItemStack convertEntityToEntitySpawnEgg(LivingEntity livingEntity) {
        Material material = Material.getMaterial(livingEntity.getType().name().toUpperCase() + "_SPAWN_EGG");
        if (material == null) {
            String entityType = livingEntity.getType().name();
            if ("PIG_ZOMBIE".equals(entityType) || "ZOMBIFIED_PIGLIN".equals(entityType)) {
                material = firstMaterial("ZOMBIFIED_PIGLIN_SPAWN_EGG", "ZOMBIE_PIGMAN_SPAWN_EGG");
            } else if ("MUSHROOM_COW".equals(entityType) || "MOOSHROOM".equals(entityType)) {
                material = firstMaterial("MOOSHROOM_SPAWN_EGG", "MUSHROOM_COW_SPAWN_EGG");
            }
        }
        if (material == null) {
            material = Material.GHAST_SPAWN_EGG;
        }
        ItemStack spawnEgg = new ItemStack(material, 1);
        return NBTManager.castEntityDataToItemStackNBT(spawnEgg, livingEntity);
    }

    private static Material firstMaterial(String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                return material;
            }
        }
        return null;
    }
}
