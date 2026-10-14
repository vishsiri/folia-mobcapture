package net.johnbrooks.mh.domain.item;

import lombok.Getter;
import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.compat.ItemMetaCompat;
import net.johnbrooks.mh.i18n.Message;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class UniqueProjectileData {
    private static final String LEGACY_TYPE_ID = "default";
    private static final String TYPE_KEY_NAME = "capture_item_type";
    private static final CaptureItemType FALLBACK_TYPE = new CaptureItemType(
            LEGACY_TYPE_ID,
            Material.EGG,
            "<gold>Mob Catcher",
            Collections.singletonList("<dark_aqua>Shoot a mob with me!"),
            100D
    );

    @Getter
    private static volatile boolean enabled;
    private static volatile String defaultType = LEGACY_TYPE_ID;
    private static volatile CaptureTypesSnapshot snapshot = new CaptureTypesSnapshot(
            Collections.emptyMap(),
            Collections.emptySet(),
            Collections.emptySet()
    );
    private static volatile NamespacedKey typeKey;

    public static void setEnabled(boolean enabled) {
        UniqueProjectileData.enabled = enabled;
    }

    public static void setDefaultType(String defaultType) {
        UniqueProjectileData.defaultType = normalize(defaultType);
    }

    public static void setTypes(Collection<CaptureItemType> captureTypes) {
        Map<String, CaptureItemType> updatedTypes = new LinkedHashMap<>();
        Set<Material> updatedMaterials = new HashSet<>();
        Set<String> updatedMaterialNames = new HashSet<>();
        for (CaptureItemType type : captureTypes) {
            // Store raw (MiniMessage / &-code) strings — ItemMetaCompat parses them natively.
            updatedTypes.put(normalize(type.getId()), new CaptureItemType(
                    normalize(type.getId()),
                    type.getMaterial(),
                    applyPlaceholders(type.getDisplayName(), type),
                    applyPlaceholdersToLore(type.getLore(), type),
                    clampChance(type.getChance())
            ));
            updatedMaterials.add(type.getMaterial());
            updatedMaterialNames.add(type.getMaterial().name());
        }
        snapshot = new CaptureTypesSnapshot(
                Collections.unmodifiableMap(updatedTypes),
                Set.copyOf(updatedMaterials),
                Set.copyOf(updatedMaterialNames)
        );
    }

    public static ItemStack spawn() {
        return spawn(defaultType, 1);
    }

    public static ItemStack spawn(String typeId, int amount) {
        CaptureItemType type = getType(typeId).orElseGet(UniqueProjectileData::defaultCaptureType);
        ItemStack itemStack = new ItemStack(type.getMaterial(), Math.max(1, Math.min(64, amount)));

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        itemMeta.getPersistentDataContainer().set(typeKey(), PersistentDataType.STRING, type.getId());
        ItemMetaCompat.setDisplayName(itemMeta, type.getDisplayName());
        if (!type.getLore().isEmpty()) {
            ItemMetaCompat.setLore(itemMeta, type.getLore());
        }
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    public static boolean isProjectile(ItemStack projectile) {
        return findType(projectile).isPresent();
    }

    public static Optional<CaptureItemType> findType(ItemStack projectile) {
        if (projectile == null || !projectile.hasItemMeta()) {
            return Optional.empty();
        }

        ItemMeta meta = projectile.getItemMeta();
        String storedType = meta.getPersistentDataContainer().get(typeKey(), PersistentDataType.STRING);
        Optional<CaptureItemType> type = getType(storedType);
        if (type.isPresent()) {
            return type;
        }

        // Fallback for legacy items without PDC: compare via legacy-serialised form.
        String legacyDisplayName = ItemMetaCompat.getDisplayName(meta);
        for (CaptureItemType candidate : snapshot.types.values()) {
            if (candidate.getMaterial() == projectile.getType()
                    && legacyDisplayName.equalsIgnoreCase(Message.colorize(candidate.getDisplayName()))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public static Optional<CaptureItemType> getType(String typeId) {
        if (typeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.types.get(normalize(typeId)));
    }

    public static Collection<CaptureItemType> getTypes() {
        return snapshot.types.values();
    }

    public static boolean isCaptureMaterial(Material material) {
        return snapshot.materials.contains(material);
    }

    public static boolean isCaptureMaterialName(String materialName) {
        return snapshot.materialNames.contains(materialName);
    }

    public static CaptureItemType defaultCaptureType() {
        Map<String, CaptureItemType> types = snapshot.types;
        CaptureItemType configuredDefault = types.get(defaultType);
        if (configuredDefault != null) {
            return configuredDefault;
        }
        for (CaptureItemType type : types.values()) {
            return type;
        }
        return FALLBACK_TYPE;
    }

    private static NamespacedKey typeKey() {
        NamespacedKey current = typeKey;
        if (current != null) {
            return current;
        }

        synchronized (UniqueProjectileData.class) {
            if (typeKey == null) {
                typeKey = new NamespacedKey(Main.plugin, TYPE_KEY_NAME);
            }
            return typeKey;
        }
    }

    private static List<String> applyPlaceholdersToLore(List<String> lore, CaptureItemType type) {
        List<String> result = new ArrayList<>(lore.size());
        lore.forEach(line -> result.add(applyPlaceholders(line, type)));
        return result;
    }

    private static String applyPlaceholders(String text, CaptureItemType type) {
        return text
                .replace("{type}", type.getId())
                .replace("{material}", type.getMaterial().name())
                .replace("{chance}", formatChance(type.getChance()));
    }

    private static String formatChance(double chance) {
        double rounded = Math.round(chance * 10D) / 10D;
        return rounded == (int) rounded ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }

    private static String normalize(String typeId) {
        return typeId.toLowerCase(Locale.ROOT);
    }

    private static double clampChance(double chance) {
        return Math.max(0D, Math.min(100D, chance));
    }

    private record CaptureTypesSnapshot(
            Map<String, CaptureItemType> types,
            Set<Material> materials,
            Set<String> materialNames
    ) {
    }
}
