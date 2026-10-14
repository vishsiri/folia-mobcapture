package net.johnbrooks.mh.persistence;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBTList;
import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.compat.ItemMetaCompat;
import net.johnbrooks.mh.compat.ReflectionInvoker;
import net.johnbrooks.mh.config.PluginConfig;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.debug.DebugLog;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class NBTManager {
    private static final String DATA_KEY = "mobcapture";
    private static final String LEGACY_DATA_KEY = "tag";
    private static final String ENTITY_TYPE_KEY = "entity_type";
    private static final String ENTITY_SNAPSHOT_KEY = "entity_snapshot";
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String ATTRIBUTE_MODIFIERS_KEY = "modifiers";
    private static volatile List<Attribute> registeredAttributes;

    public static boolean preload() {
        return NBT.preloadApi();
    }

    public static boolean isSpawnEgg(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == null || !itemStack.getType().name().contains("SPAWN_EGG")) {
            return false;
        }

        try {
            return NBT.get(itemStack, nbt -> {
                ReadableNBT entityDetails = getEntityDetails(nbt);
                return entityDetails != null && entityDetails.hasTag(ENTITY_TYPE_KEY);
            });
        } catch (RuntimeException ex) {
            Main.logger.warning("Failed to read a possible captured egg; blocking vanilla use: " + ex.getMessage());
            return true;
        }
    }

    public static LivingEntity spawnEntityFromNBTData(ItemStack spawnItem, Location target) {
        if (spawnItem == null) {
            Main.logger.warning("NULL spawn item passed to #spawnEntityFromNBTData().");
            return null;
        }

        try {
            ReadWriteNBT entityDetails = readEntityDetailsSnapshot(spawnItem);
            if (entityDetails == null || !entityDetails.hasTag(ENTITY_TYPE_KEY)) {
                Main.logger.warning("Spawn item does not have MobCapture entity data.");
                return null;
            }

            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityDetails.getString(ENTITY_TYPE_KEY));
            } catch (IllegalArgumentException ex) {
                Main.logger.warning("Captured egg contains an invalid entity type.");
                return null;
            }

            Location spawnLocation = target.clone().add(0, 1f, 0);
            if (entityDetails.hasTag(ENTITY_SNAPSHOT_KEY)) {
                return spawnEntityFromSnapshot(entityDetails, entityType, spawnLocation);
            }

            Entity spawnedEntity = target.getWorld().spawnEntity(spawnLocation, entityType);
            if (!(spawnedEntity instanceof LivingEntity)) {
                spawnedEntity.remove();
                Main.logger.warning("Captured egg entity type is not a living entity: " + entityType);
                return null;
            }

            LivingEntity livingEntity = (LivingEntity) spawnedEntity;
            try {
                applyNBTDataToEntity(livingEntity, entityDetails);
                return livingEntity;
            } catch (RuntimeException ex) {
                livingEntity.remove();
                throw ex;
            }
        } catch (RuntimeException ex) {
            if (DebugLog.isEnabled()) {
                Main.logger.log(Level.WARNING, "Failed to restore captured entity.", ex);
            } else {
                Main.logger.warning("Failed to restore captured entity: " + ex.getMessage());
            }
            return null;
        }
    }

    private static LivingEntity spawnEntityFromSnapshot(
            ReadableNBT entityDetails,
            EntityType expectedType,
            Location spawnLocation
    ) {
        ReadableNBT snapshotData = entityDetails.getCompound(ENTITY_SNAPSHOT_KEY);
        if (snapshotData == null) {
            throw new IllegalArgumentException("Captured entity snapshot is missing.");
        }

        EntitySnapshot snapshot = Bukkit.getEntityFactory().createEntitySnapshot(snapshotData.toString());
        if (snapshot.getEntityType() != expectedType) {
            throw new IllegalArgumentException("Captured entity snapshot type does not match " + expectedType + ".");
        }

        Entity spawnedEntity = snapshot.createEntity(spawnLocation);
        if (!(spawnedEntity instanceof LivingEntity)) {
            spawnedEntity.remove();
            throw new IllegalArgumentException("Captured entity snapshot is not a living entity.");
        }

        DebugLog.info("Restored complete entity snapshot: type=" + expectedType.name()
                + ", uuid=" + spawnedEntity.getUniqueId() + ".");
        return (LivingEntity) spawnedEntity;
    }

    private static ReadWriteNBT readEntityDetailsSnapshot(ItemStack spawnItem) {
        ReadWriteNBT snapshot = NBT.createNBTObject();
        boolean found = NBT.get(spawnItem, nbt -> {
            ReadableNBT storedDetails = getEntityDetails(nbt);
            if (storedDetails == null) {
                return false;
            }

            snapshot.mergeCompound(storedDetails);
            return true;
        });
        return found ? snapshot : null;
    }

    private static ReadableNBT getEntityDetails(ReadableNBT nbt) {
        if (nbt.hasTag(DATA_KEY)) {
            return nbt.getCompound(DATA_KEY);
        }

        if (nbt.hasTag(LEGACY_DATA_KEY)) {
            ReadableNBT legacy = nbt.getCompound(LEGACY_DATA_KEY);
            if (legacy != null && legacy.hasTag("entity type")) {
                return legacy;
            }
        }

        return null;
    }

    private static void applyNBTDataToEntity(LivingEntity livingEntity, ReadableNBT entityDetails) {
        setCustomName(livingEntity, getString(entityDetails, "custom_name", "custom name"));
        setBooleanIfPresent(entityDetails, "ai", livingEntity::setAI);
        setMaxHealth(livingEntity, getDouble(entityDetails, "max_health", "max health"));
        Double health = getDouble(entityDetails, "health", "health");
        setBooleanIfPresent(entityDetails, "glowing", livingEntity::setGlowing);

        applyPotionEffects(livingEntity, entityDetails);

        if (livingEntity instanceof Ageable && hasAny(entityDetails, "age", "age")) {
            ((Ageable) livingEntity).setAge(getInteger(entityDetails, "age", "age", 0));
        }

        if (livingEntity instanceof Tameable) {
            Tameable tameable = (Tameable) livingEntity;
            tameable.setTamed(getBoolean(entityDetails, "tamed", "tamed", false));
            setTameableOwner(tameable, getString(entityDetails, "owner", "owner"));
        }

        if (livingEntity instanceof Wolf) {
            Wolf wolf = (Wolf) livingEntity;
            wolf.setSitting(getBoolean(entityDetails, "wolf_sitting", "is sitting", false));
            setWolfAngry(wolf, getBoolean(entityDetails, "wolf_angry", "is angry", false));
            if (wolf.isTamed()) {
                setEnumValue(DyeColor.class, getString(entityDetails, "wolf_collar_color", "color"), wolf::setCollarColor);
            }
        } else if (livingEntity instanceof Pig) {
            ((Pig) livingEntity).setSaddle(getBoolean(entityDetails, "pig_saddled", "saddled", false));
        } else if (livingEntity instanceof Sheep) {
            Sheep sheep = (Sheep) livingEntity;
            sheep.setSheared(getBoolean(entityDetails, "sheep_sheared", "sheared", false));
            setEnumValue(DyeColor.class, getString(entityDetails, "sheep_color", "color"), sheep::setColor);
        } else if (livingEntity instanceof Ocelot) {
            invokeBooleanMethod(livingEntity, "setSitting", getBoolean(entityDetails, "ocelot_sitting", "sitting", false));
            invokeEnumMethod(livingEntity, "setCatType", getString(entityDetails, "ocelot_type", "cat type"));
        } else if (livingEntity instanceof Rabbit) {
            setEnumValue(Rabbit.Type.class, getString(entityDetails, "rabbit_type", "rabbit type"), ((Rabbit) livingEntity)::setRabbitType);
        } else if (livingEntity instanceof AbstractHorse) {
            AbstractHorse horse = (AbstractHorse) livingEntity;
            horse.setJumpStrength(getDouble(entityDetails, "horse_jump_strength", "jump strength", horse.getJumpStrength()));
            AttributeInstance movementSpeed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (movementSpeed != null) {
                movementSpeed.setBaseValue(getDouble(entityDetails, "horse_speed", "speed", movementSpeed.getBaseValue()));
            }
            horse.setTamed(getBoolean(entityDetails, "horse_tamed", "tamed", horse.isTamed()));
        } else if (livingEntity instanceof Villager) {
            applyVillagerData((Villager) livingEntity, entityDetails);
        } else if (livingEntity instanceof Creeper) {
            ((Creeper) livingEntity).setPowered(getBoolean(entityDetails, "creeper_charged", "charged", false));
        } else if (livingEntity instanceof Slime) {
            ((Slime) livingEntity).setSize(getInteger(entityDetails, "slime_size", "size", 1));
        } else if (livingEntity instanceof ZombieVillager) {
            invokeEnumMethod(livingEntity, "setVillagerProfession", getString(entityDetails, "zombie_villager_profession", "profession"));
        } else if (livingEntity instanceof Parrot) {
            setEnumValue(Parrot.Variant.class, getString(entityDetails, "parrot_variant", "variant"), ((Parrot) livingEntity)::setVariant);
        }

        if (livingEntity instanceof Llama) {
            Llama llama = (Llama) livingEntity;
            setEnumValue(Llama.Color.class, getString(entityDetails, "llama_color", "color"), llama::setColor);
            llama.setStrength(getInteger(entityDetails, "llama_strength", "strength", llama.getStrength()));
        } else if (livingEntity instanceof Horse) {
            Horse horse = (Horse) livingEntity;
            setEnumValue(Horse.Color.class, getString(entityDetails, "horse_color", "color"), horse::setColor);
            setEnumValue(Horse.Style.class, getString(entityDetails, "horse_style", "style"), horse::setStyle);
        }

        applyInventoryData(livingEntity, entityDetails);
        applyEquipmentData(livingEntity, entityDetails);
        applyAttributeData(livingEntity, entityDetails);
        applyHealth(livingEntity, health);
    }

    public static ItemStack castEntityDataToItemStackNBT(ItemStack itemStack, LivingEntity livingEntity) {
        final String entityName = getEntityName(livingEntity);
        final ReadWriteNBT entitySnapshot = createEntitySnapshot(livingEntity);
        if (entitySnapshot == null) {
            return null;
        }

        NBT.modify(itemStack, nbt -> {
            ReadWriteNBT entityDetails = nbt.getOrCreateCompound(DATA_KEY);
            entityDetails.clearNBT();
            generateNBTTagCompound(entityDetails, livingEntity, entitySnapshot);

            List<String> lore = createLore(livingEntity, entityDetails);
            String displayName = Settings.capturedEgg.getDisplayName()
                    .replace("{entity}", entityName);
            nbt.modifyMeta((readOnlyNbt, meta) -> {
                ItemMetaCompat.setDisplayName(meta, displayName);
                ItemMetaCompat.setLore(meta, lore);
            });
        });

        return itemStack;
    }

    private static ReadWriteNBT createEntitySnapshot(LivingEntity livingEntity) {
        try {
            EntitySnapshot snapshot = livingEntity.createSnapshot();
            if (snapshot == null) {
                Main.logger.warning("Unable to create a complete snapshot for captured "
                        + livingEntity.getType().name() + "; capture was cancelled.");
                return null;
            }

            ReadWriteNBT snapshotData = NBT.parseNBT(snapshot.getAsString());
            DebugLog.info("Stored complete entity snapshot: type=" + livingEntity.getType().name()
                    + ", uuid=" + livingEntity.getUniqueId() + ".");
            return snapshotData;
        } catch (RuntimeException ex) {
            if (DebugLog.isEnabled()) {
                Main.logger.log(Level.WARNING,
                        "Unable to create a complete captured entity snapshot; capture was cancelled.", ex);
            } else {
                Main.logger.warning("Unable to create a complete captured entity snapshot; "
                        + "capture was cancelled: " + ex.getMessage());
            }
            return null;
        }
    }

    private static void generateNBTTagCompound(
            ReadWriteNBT entityDetails,
            LivingEntity livingEntity,
            ReadableNBT entitySnapshot
    ) {
        String customName = getCustomName(livingEntity);
        if (customName != null) {
            entityDetails.setString("custom_name", customName);
        }
        entityDetails.setString(ENTITY_TYPE_KEY, livingEntity.getType().name());
        if (entitySnapshot != null) {
            entityDetails.getOrCreateCompound(ENTITY_SNAPSHOT_KEY).mergeCompound(entitySnapshot);
        }
        entityDetails.setBoolean("ai", livingEntity.hasAI());
        entityDetails.setDouble("health", livingEntity.getHealth());
        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            entityDetails.setDouble("max_health", maxHealth.getBaseValue());
        }
        storeAttributeData(entityDetails, livingEntity);
        entityDetails.setBoolean("glowing", livingEntity.isGlowing());

        ReadWriteNBTCompoundList potionEffectList = entityDetails.getCompoundList("potion_effects");
        potionEffectList.clear();
        for (PotionEffect potionEffect : livingEntity.getActivePotionEffects()) {
            ReadWriteNBT potionEffectCompound = potionEffectList.addCompound();
            potionEffectCompound.setInteger("duration", potionEffect.getDuration());
            potionEffectCompound.setInteger("amplifier", potionEffect.getAmplifier());
            potionEffectCompound.setString("type", getPotionEffectTypeName(potionEffect.getType()));
            potionEffectCompound.setBoolean("ambient", potionEffect.isAmbient());
            potionEffectCompound.setBoolean("particles", potionEffect.hasParticles());
        }

        if (livingEntity instanceof Ageable) {
            entityDetails.setInteger("age", ((Ageable) livingEntity).getAge());
        }
        if (livingEntity instanceof InventoryHolder) {
            storeInventoryData(entityDetails, (InventoryHolder) livingEntity);
        }
        storeEquipmentData(entityDetails, livingEntity);

        if (livingEntity instanceof Wolf) {
            Wolf wolf = (Wolf) livingEntity;
            entityDetails.setBoolean("wolf_angry", wolf.isAngry());
            entityDetails.setBoolean("wolf_sitting", wolf.isSitting());
            entityDetails.setString("wolf_collar_color", wolf.getCollarColor().name());
        } else if (livingEntity instanceof Sheep) {
            Sheep sheep = (Sheep) livingEntity;
            entityDetails.setString("sheep_color", sheep.getColor().name());
            entityDetails.setBoolean("sheep_sheared", sheep.isSheared());
        } else if (livingEntity instanceof Pig) {
            entityDetails.setBoolean("pig_saddled", ((Pig) livingEntity).hasSaddle());
        } else if (livingEntity instanceof Ocelot) {
            entityDetails.setBoolean("ocelot_sitting", invokeBooleanGetter(livingEntity, "isSitting", false));
            entityDetails.setString("ocelot_type", invokeEnumGetter(livingEntity, "getCatType", "WILD_OCELOT"));
        } else if (livingEntity instanceof Rabbit) {
            entityDetails.setString("rabbit_type", ((Rabbit) livingEntity).getRabbitType().name());
        } else if (livingEntity instanceof AbstractHorse) {
            AbstractHorse horse = (AbstractHorse) livingEntity;
            entityDetails.setDouble("horse_jump_strength", horse.getJumpStrength());
            AttributeInstance speed = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                entityDetails.setDouble("horse_speed", speed.getBaseValue());
            }
            entityDetails.setBoolean("horse_tamed", horse.isTamed());
        } else if (livingEntity instanceof Villager) {
            storeVillagerData(entityDetails, (Villager) livingEntity);
        } else if (livingEntity instanceof Creeper) {
            entityDetails.setBoolean("creeper_charged", ((Creeper) livingEntity).isPowered());
        } else if (livingEntity instanceof Slime) {
            entityDetails.setInteger("slime_size", ((Slime) livingEntity).getSize());
        } else if (livingEntity instanceof ZombieVillager) {
            entityDetails.setString("zombie_villager_profession", invokeEnumGetter(livingEntity, "getVillagerProfession", "FARMER"));
        } else if (livingEntity instanceof Parrot) {
            entityDetails.setString("parrot_variant", ((Parrot) livingEntity).getVariant().name());
        }

        if (livingEntity instanceof Tameable) {
            Tameable tameable = (Tameable) livingEntity;
            entityDetails.setBoolean("tamed", tameable.isTamed());
            if (tameable.isTamed() && tameable.getOwner() != null) {
                entityDetails.setString("owner", tameable.getOwner().getUniqueId().toString());
            }
        }

        if (livingEntity instanceof Llama) {
            Llama llama = (Llama) livingEntity;
            entityDetails.setString("llama_color", llama.getColor().name());
            entityDetails.setInteger("llama_strength", llama.getStrength());
        } else if (livingEntity instanceof Horse) {
            Horse horse = (Horse) livingEntity;
            entityDetails.setString("horse_color", horse.getColor().name());
            entityDetails.setString("horse_style", horse.getStyle().name());
        }
    }

    private static void applyPotionEffects(LivingEntity livingEntity, ReadableNBT entityDetails) {
        ReadableNBTList<ReadWriteNBT> potionEffectList = getCompoundList(entityDetails, "potion_effects", "potion effects");
        if (potionEffectList == null) {
            return;
        }

        for (int i = 0; i < potionEffectList.size(); i++) {
            ReadableNBT potionEffectCompound = potionEffectList.get(i);
            PotionEffectType potionEffectType = getPotionEffectType(potionEffectCompound.getString("type"));
            if (potionEffectType == null) {
                continue;
            }

            PotionEffect potionEffect = new PotionEffect(
                    potionEffectType,
                    potionEffectCompound.getOrDefault("duration", 0),
                    potionEffectCompound.getOrDefault("amplifier", 0),
                    potionEffectCompound.getOrDefault("ambient", false),
                    potionEffectCompound.getOrDefault("particles", true)
            );
            livingEntity.addPotionEffect(potionEffect);
        }
    }

    private static void storeAttributeData(ReadWriteNBT entityDetails, LivingEntity livingEntity) {
        ReadWriteNBTCompoundList attributes = entityDetails.getCompoundList(ATTRIBUTES_KEY);
        attributes.clear();
        int storedAttributes = 0;
        int storedModifiers = 0;

        for (Attribute attribute : registeredAttributes()) {
            AttributeInstance instance = livingEntity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }

            ReadWriteNBT attributeData = attributes.addCompound();
            attributeData.setString("key", attribute.getKey().toString());
            attributeData.setDouble("base", instance.getBaseValue());
            storedAttributes++;

            ReadWriteNBTCompoundList modifiers = attributeData.getCompoundList(ATTRIBUTE_MODIFIERS_KEY);
            for (AttributeModifier modifier : instance.getModifiers()) {
                ReadWriteNBT modifierData = modifiers.addCompound();
                modifierData.setString("key", modifier.getKey().toString());
                modifierData.setDouble("amount", modifier.getAmount());
                modifierData.setString("operation", modifier.getOperation().name());
                EquipmentSlotGroup slotGroup = modifier.getSlotGroup();
                modifierData.setString("slot_group", slotGroup == null ? "any" : slotGroup.toString());
                storedModifiers++;
            }
        }

        if (DebugLog.isEnabled()) {
            DebugLog.info("Stored captured attributes: entity=" + livingEntity.getType().name()
                    + ", uuid=" + livingEntity.getUniqueId()
                    + ", attributes=" + storedAttributes + ", modifiers=" + storedModifiers + ".");
        }
    }

    private static List<Attribute> registeredAttributes() {
        List<Attribute> current = registeredAttributes;
        if (current != null) {
            return current;
        }

        synchronized (NBTManager.class) {
            if (registeredAttributes == null) {
                List<Attribute> loaded = new ArrayList<>();
                Registry.ATTRIBUTE.forEach(loaded::add);
                registeredAttributes = List.copyOf(loaded);
            }
            return registeredAttributes;
        }
    }

    private static void applyAttributeData(LivingEntity livingEntity, ReadableNBT entityDetails) {
        ReadableNBTList<ReadWriteNBT> attributes = getCompoundList(entityDetails, ATTRIBUTES_KEY, null);
        if (attributes == null) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("No captured attribute snapshot found: entity=" + livingEntity.getType().name()
                        + ", uuid=" + livingEntity.getUniqueId() + ".");
            }
            return;
        }

        int restoredAttributes = 0;
        int restoredModifiers = 0;
        for (int i = 0; i < attributes.size(); i++) {
            ReadableNBT attributeData = attributes.get(i);
            try {
                NamespacedKey attributeKey = NamespacedKey.fromString(attributeData.getString("key"));
                Attribute attribute = attributeKey == null ? null : Registry.ATTRIBUTE.get(attributeKey);
                AttributeInstance instance = attribute == null ? null : livingEntity.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }

                double baseValue = attributeData.getDouble("base");
                if (Double.isFinite(baseValue)) {
                    instance.setBaseValue(baseValue);
                }
                restoredModifiers += applyAttributeModifiers(instance, attributeData);
                restoredAttributes++;
            } catch (IllegalArgumentException ex) {
                Main.logger.warning("Failed to restore captured attribute: " + ex.getMessage());
            }
        }

        if (DebugLog.isEnabled()) {
            DebugLog.info("Restored captured attributes: entity=" + livingEntity.getType().name()
                    + ", uuid=" + livingEntity.getUniqueId()
                    + ", attributes=" + restoredAttributes + "/" + attributes.size()
                    + ", modifiers=" + restoredModifiers + ".");
        }
    }

    private static int applyAttributeModifiers(AttributeInstance instance, ReadableNBT attributeData) {
        ReadableNBTList<ReadWriteNBT> modifiers = getCompoundList(attributeData, ATTRIBUTE_MODIFIERS_KEY, null);
        if (modifiers == null) {
            return 0;
        }

        int restoredModifiers = 0;
        for (int i = 0; i < modifiers.size(); i++) {
            ReadableNBT modifierData = modifiers.get(i);
            try {
                NamespacedKey modifierKey = NamespacedKey.fromString(modifierData.getString("key"));
                double amount = modifierData.getDouble("amount");
                if (modifierKey == null || !Double.isFinite(amount)) {
                    continue;
                }

                AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(modifierData.getString("operation"));
                EquipmentSlotGroup slotGroup = EquipmentSlotGroup.getByName(modifierData.getString("slot_group"));
                if (slotGroup == null) {
                    slotGroup = EquipmentSlotGroup.ANY;
                }

                AttributeModifier restored = new AttributeModifier(modifierKey, amount, operation, slotGroup);
                AttributeModifier existing = instance.getModifier(modifierKey);
                if (sameModifier(existing, restored)) {
                    restoredModifiers++;
                    continue;
                }
                if (existing != null) {
                    instance.removeModifier(existing);
                }
                instance.addModifier(restored);
                restoredModifiers++;
            } catch (IllegalArgumentException ex) {
                Main.logger.warning("Failed to restore captured attribute modifier: " + ex.getMessage());
            }
        }
        return restoredModifiers;
    }

    private static boolean sameModifier(AttributeModifier first, AttributeModifier second) {
        return first != null
                && Double.compare(first.getAmount(), second.getAmount()) == 0
                && first.getOperation() == second.getOperation()
                && Objects.equals(first.getSlotGroup(), second.getSlotGroup());
    }

    private static void applyHealth(LivingEntity livingEntity, Double health) {
        if (health == null || !Double.isFinite(health)) {
            return;
        }

        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        double cappedHealth = maxHealth == null ? health : Math.min(health, maxHealth.getValue());
        livingEntity.setHealth(Math.max(0.1d, cappedHealth));
    }

    private static void storeInventoryData(ReadWriteNBT entityDetails, InventoryHolder inventoryHolder) {
        ReadWriteNBTCompoundList inventoryList = entityDetails.getCompoundList("inventory");
        inventoryList.clear();
        ItemStack[] contents = inventoryHolder.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack itemStack = contents[i];
            if (itemStack != null && itemStack.getType() != Material.AIR) {
                ReadWriteNBT entry = inventoryList.addCompound();
                entry.setInteger("slot", i);
                entry.setString("item", NBT.itemStackToNBT(itemStack).toString());
            }
        }
    }

    private static void applyInventoryData(LivingEntity livingEntity, ReadableNBT entityDetails) {
        if (!(livingEntity instanceof InventoryHolder)) {
            return;
        }

        ReadableNBTList<ReadWriteNBT> inventoryList = getCompoundList(entityDetails, "inventory", null);
        if (inventoryList == null) {
            return;
        }

        InventoryHolder inventoryHolder = (InventoryHolder) livingEntity;
        for (int i = 0; i < inventoryList.size(); i++) {
            ReadableNBT entry = inventoryList.get(i);
            try {
                int slot = entry.getOrDefault("slot", -1);
                if (slot >= 0 && slot < inventoryHolder.getInventory().getSize()) {
                    inventoryHolder.getInventory().setItem(slot, NBT.itemStackFromNBT(NBT.parseNBT(entry.getString("item"))));
                }
            } catch (Exception ex) {
                Main.logger.warning("Failed to restore captured inventory item: " + ex.getMessage());
            }
        }
    }

    private static void storeEquipmentData(ReadWriteNBT entityDetails, LivingEntity livingEntity) {
        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment == null) {
            return;
        }

        ReadWriteNBT equipmentData = entityDetails.getOrCreateCompound("equipment");
        equipmentData.clearNBT();
        storeEquipmentSlot(equipmentData, "main_hand", equipment.getItemInMainHand(), equipment.getItemInMainHandDropChance());
        storeEquipmentSlot(equipmentData, "off_hand", equipment.getItemInOffHand(), equipment.getItemInOffHandDropChance());
        storeEquipmentSlot(equipmentData, "helmet", equipment.getHelmet(), equipment.getHelmetDropChance());
        storeEquipmentSlot(equipmentData, "chestplate", equipment.getChestplate(), equipment.getChestplateDropChance());
        storeEquipmentSlot(equipmentData, "leggings", equipment.getLeggings(), equipment.getLeggingsDropChance());
        storeEquipmentSlot(equipmentData, "boots", equipment.getBoots(), equipment.getBootsDropChance());
    }

    private static void storeEquipmentSlot(ReadWriteNBT equipmentData, String key, ItemStack itemStack, float dropChance) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return;
        }

        ReadWriteNBT slotData = equipmentData.getOrCreateCompound(key);
        slotData.setString("item", NBT.itemStackToNBT(itemStack).toString());
        slotData.setFloat("drop_chance", dropChance);
    }

    private static void applyEquipmentData(LivingEntity livingEntity, ReadableNBT entityDetails) {
        if (!entityDetails.hasTag("equipment")) {
            return;
        }

        EntityEquipment equipment = livingEntity.getEquipment();
        if (equipment == null) {
            return;
        }

        ReadableNBT equipmentData = entityDetails.getCompound("equipment");
        applyEquipmentSlot(equipmentData, "main_hand", equipment, EquipmentSlotApplier.MAIN_HAND);
        applyEquipmentSlot(equipmentData, "off_hand", equipment, EquipmentSlotApplier.OFF_HAND);
        applyEquipmentSlot(equipmentData, "helmet", equipment, EquipmentSlotApplier.HELMET);
        applyEquipmentSlot(equipmentData, "chestplate", equipment, EquipmentSlotApplier.CHESTPLATE);
        applyEquipmentSlot(equipmentData, "leggings", equipment, EquipmentSlotApplier.LEGGINGS);
        applyEquipmentSlot(equipmentData, "boots", equipment, EquipmentSlotApplier.BOOTS);
    }

    private static void applyEquipmentSlot(ReadableNBT equipmentData, String key, EntityEquipment equipment, EquipmentSlotApplier applier) {
        if (!equipmentData.hasTag(key)) {
            return;
        }

        try {
            ReadableNBT slotData = equipmentData.getCompound(key);
            ItemStack itemStack = NBT.itemStackFromNBT(NBT.parseNBT(slotData.getString("item")));
            float dropChance = slotData.getOrDefault("drop_chance", 0f);
            applier.apply(equipment, itemStack, dropChance);
        } catch (Exception ex) {
            Main.logger.warning("Failed to restore captured equipment item: " + ex.getMessage());
        }
    }

    private static void storeVillagerData(ReadWriteNBT entityDetails, Villager villager) {
        entityDetails.setString("villager_profession", villager.getProfession().getKey().getKey().toUpperCase(Locale.ROOT));
        entityDetails.setInteger("villager_riches", invokeIntGetter(villager, "getRiches", 0));

        ReadWriteNBTCompoundList recipeList = entityDetails.getCompoundList("villager_recipes");
        recipeList.clear();
        for (MerchantRecipe recipe : villager.getRecipes()) {
            ReadWriteNBT recipeCompound = recipeList.addCompound();
            recipeCompound.setInteger("uses", recipe.getUses());
            recipeCompound.setInteger("max_uses", recipe.getMaxUses());
            recipeCompound.setBoolean("experience_reward", recipe.hasExperienceReward());
            recipeCompound.setString("result", NBT.itemStackToNBT(recipe.getResult()).toString());

            ReadWriteNBTCompoundList ingredients = recipeCompound.getCompoundList("ingredients");
            for (ItemStack ingredient : recipe.getIngredients()) {
                ReadWriteNBT ingredientCompound = ingredients.addCompound();
                ingredientCompound.setString("item", NBT.itemStackToNBT(ingredient).toString());
            }
        }
    }

    private static void applyVillagerData(Villager villager, ReadableNBT entityDetails) {
        setVillagerProfession(villager, getString(entityDetails, "villager_profession", "profession"));
        invokeIntMethod(villager, "setRiches", getInteger(entityDetails, "villager_riches", "riches", 0));

        ReadableNBTList<ReadWriteNBT> recipeList = getCompoundList(entityDetails, "villager_recipes", "recipes");
        if (recipeList == null) {
            return;
        }

        List<MerchantRecipe> merchantRecipeList = new ArrayList<>();
        for (int i = 0; i < recipeList.size(); i++) {
            try {
                ReadableNBT recipeCompound = recipeList.get(i);
                ItemStack result = recipeCompound.hasTag("result")
                        ? NBT.itemStackFromNBT(NBT.parseNBT(recipeCompound.getString("result")))
                        : legacyMaterialStack(recipeCompound.getString("result"));
                MerchantRecipe merchantRecipe = new MerchantRecipe(result, recipeCompound.getOrDefault("max_uses", recipeCompound.getOrDefault("max uses", 0)));
                merchantRecipe.setUses(recipeCompound.getOrDefault("uses", 0));
                merchantRecipe.setExperienceReward(recipeCompound.getOrDefault("experience_reward", recipeCompound.getOrDefault("experience reward", true)));
                merchantRecipe.setIngredients(readIngredients(recipeCompound));
                merchantRecipeList.add(merchantRecipe);
            } catch (Exception ex) {
                Main.logger.warning("Failed to restore villager recipe: " + ex.getMessage());
            }
        }
        villager.setRecipes(merchantRecipeList);
    }

    private static List<ItemStack> readIngredients(ReadableNBT recipeCompound) {
        List<ItemStack> ingredients = new ArrayList<>();
        ReadableNBTList<ReadWriteNBT> ingredientList = getCompoundList(recipeCompound, "ingredients", null);
        if (ingredientList != null) {
            for (int i = 0; i < ingredientList.size(); i++) {
                ingredients.add(NBT.itemStackFromNBT(NBT.parseNBT(ingredientList.get(i).getString("item"))));
            }
            return ingredients;
        }

        ReadableNBTList<String> legacyMaterials = recipeCompound.getStringList("materials");
        if (legacyMaterials != null) {
            for (int i = 0; i < legacyMaterials.size(); i++) {
                ingredients.add(legacyMaterialStack(legacyMaterials.get(i)));
            }
        }
        return ingredients;
    }

    private static ItemStack legacyMaterialStack(String value) {
        String[] parts = value.split("\\.");
        return new ItemStack(Material.valueOf(parts[0]), Integer.parseInt(parts[1]));
    }

    private static List<String> createLore(LivingEntity livingEntity, ReadableNBT entityDetails) {
        PluginConfig.CapturedEgg cfg = Settings.capturedEgg;
        List<String> lore = new ArrayList<>();

        String healthData = formatNumber(livingEntity.getHealth());
        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        String maxHealthData = maxHealth == null ? "?" : formatNumber(maxHealth.getBaseValue());

        for (String line : cfg.getLore()) {
            lore.add(line
                    .replace("{entity_type}", livingEntity.getType().name())
                    .replace("{health}", healthData)
                    .replace("{max_health}", maxHealthData));
        }

        if (livingEntity instanceof Ageable && !((Ageable) livingEntity).isAdult()) {
            String template = cfg.getLoreBaby();
            if (!template.isEmpty()) lore.add(template);
        }

        if (livingEntity instanceof Tameable) {
            String template = cfg.getLoreTamed();
            if (!template.isEmpty()) {
                lore.add(template.replace("{tamed}", ((Tameable) livingEntity).isTamed() ? "Yes" : "No"));
            }
        }

        if (livingEntity instanceof Parrot) {
            String template = cfg.getLoreParrotColor();
            if (!template.isEmpty()) {
                lore.add(template.replace("{color}", ((Parrot) livingEntity).getVariant().name()));
            }
        }

        ReadableNBTList<ReadWriteNBT> potionEffectList = getCompoundList(entityDetails, "potion_effects", "potion effects");
        if (potionEffectList != null) {
            String template = cfg.getLorePotionEffect();
            for (int i = 0; i < potionEffectList.size(); i++) {
                if (template.isEmpty()) break;
                ReadableNBT potionEffectComponent = potionEffectList.get(i);
                int duration = potionEffectComponent.getOrDefault("duration", 0);
                String effectName = potionEffectComponent.getString("type").replace("_", " ").toLowerCase();
                effectName = effectName.substring(0, 1).toUpperCase() + effectName.substring(1);
                lore.add(template
                        .replace("{effect}", effectName)
                        .replace("{duration}", String.valueOf(duration / 20)));
            }
        }
        return lore;
    }

    private static String getEntityName(LivingEntity livingEntity) {
        String customName = getCustomName(livingEntity);
        if (customName != null) {
            return customName;
        }

        String entityName = livingEntity.getType().name().replace("_", " ").toLowerCase();
        return entityName.substring(0, 1).toUpperCase() + entityName.substring(1);
    }

    private static void setMaxHealth(LivingEntity livingEntity, Double value) {
        if (value == null) {
            return;
        }
        AttributeInstance maxHealth = livingEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(value);
        }
    }

    private static void setTameableOwner(Tameable tameable, String owner) {
        if (!tameable.isTamed() || owner == null || owner.isEmpty()) {
            return;
        }

        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(UUID.fromString(owner));
            if (offlinePlayer != null) {
                tameable.setOwner(offlinePlayer);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void setWolfAngry(Wolf wolf, boolean angry) {
        invokeBooleanMethod(wolf, "setAngry", angry);
    }

    private static void setCustomName(LivingEntity livingEntity, String customName) {
        if (customName != null && !customName.isEmpty()) {
            invoke(livingEntity, "setCustomName", new Class[]{String.class}, new Object[]{customName});
        }
    }

    private static String getCustomName(LivingEntity livingEntity) {
        Object value = invoke(livingEntity, "getCustomName", new Class[0], new Object[0]);
        return value instanceof String ? (String) value : null;
    }

    private static String getPotionEffectTypeName(PotionEffectType type) {
        Object key = invoke(type, "getKey", new Class[0], new Object[0]);
        Object keyValue = key == null ? null : invoke(key, "getKey", new Class[0], new Object[0]);
        if (keyValue instanceof String) {
            return ((String) keyValue).toUpperCase();
        }

        Object name = invoke(type, "getName", new Class[0], new Object[0]);
        return name instanceof String ? (String) name : type.toString();
    }

    private static PotionEffectType getPotionEffectType(String type) {
        Object namespacedKey = invokeStatic("org.bukkit.NamespacedKey", "minecraft", new Class[]{String.class}, new Object[]{type.toLowerCase()});
        Object byKey = namespacedKey == null ? null : invokeStatic(PotionEffectType.class, "getByKey", new Class[]{namespacedKey.getClass()}, new Object[]{namespacedKey});
        if (byKey instanceof PotionEffectType) {
            return (PotionEffectType) byKey;
        }

        Object byName = invokeStatic(PotionEffectType.class, "getByName", new Class[]{String.class}, new Object[]{type});
        return byName instanceof PotionEffectType ? (PotionEffectType) byName : null;
    }

    private static void invokeBooleanMethod(Object target, String methodName, boolean value) {
        invoke(target, methodName, new Class[]{boolean.class}, new Object[]{value});
    }

    private static void invokeIntMethod(Object target, String methodName, int value) {
        invoke(target, methodName, new Class[]{int.class}, new Object[]{value});
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void invokeEnumMethod(Object target, String methodName, String enumValue) {
        if (enumValue == null || enumValue.isEmpty()) {
            return;
        }

        try {
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isEnum()) {
                    Object value = Enum.valueOf((Class<? extends Enum>) method.getParameterTypes()[0], enumValue);
                    method.invoke(target, value);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            return ReflectionInvoker.invoke(target, methodName, parameterTypes, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object invokeStatic(Class<?> target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        return ReflectionInvoker.invokeStatic(target, methodName, parameterTypes, args);
    }

    private static Object invokeStatic(String className, String methodName, Class<?>[] parameterTypes, Object[] args) {
        return ReflectionInvoker.invokeStatic(className, methodName, parameterTypes, args);
    }

    private static boolean invokeBooleanGetter(Object target, String methodName, boolean defaultValue) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int invokeIntGetter(Object target, String methodName, int defaultValue) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String invokeEnumGetter(Object target, String methodName, String defaultValue) {
        Object value = invoke(target, methodName, new Class[0], new Object[0]);
        return value instanceof Enum ? ((Enum<?>) value).name() : defaultValue;
    }

    private static ReadableNBTList<ReadWriteNBT> getCompoundList(ReadableNBT nbt, String key, String legacyKey) {
        if (nbt.hasTag(key)) {
            return nbt.getCompoundList(key);
        }
        if (legacyKey != null && nbt.hasTag(legacyKey)) {
            return nbt.getCompoundList(legacyKey);
        }
        return null;
    }

    private static boolean hasAny(ReadableNBT nbt, String key, String legacyKey) {
        return nbt.hasTag(key) || (legacyKey != null && nbt.hasTag(legacyKey));
    }

    private static String getString(ReadableNBT nbt, String key, String legacyKey) {
        if (nbt.hasTag(key)) {
            return nbt.getString(key);
        }
        if (legacyKey != null && nbt.hasTag(legacyKey)) {
            return nbt.getString(legacyKey);
        }
        return null;
    }

    private static Double getDouble(ReadableNBT nbt, String key, String legacyKey) {
        if (nbt.hasTag(key)) {
            return nbt.getDouble(key);
        }
        if (legacyKey != null && nbt.hasTag(legacyKey)) {
            return nbt.getDouble(legacyKey);
        }
        return null;
    }

    private static double getDouble(ReadableNBT nbt, String key, String legacyKey, double defaultValue) {
        Double value = getDouble(nbt, key, legacyKey);
        return value == null ? defaultValue : value;
    }

    private static int getInteger(ReadableNBT nbt, String key, String legacyKey, int defaultValue) {
        if (nbt.hasTag(key)) {
            return nbt.getInteger(key);
        }
        if (legacyKey != null && nbt.hasTag(legacyKey)) {
            return nbt.getInteger(legacyKey);
        }
        return defaultValue;
    }

    private static boolean getBoolean(ReadableNBT nbt, String key, String legacyKey, boolean defaultValue) {
        if (nbt.hasTag(key)) {
            return nbt.getBoolean(key);
        }
        if (legacyKey != null && nbt.hasTag(legacyKey)) {
            return nbt.getBoolean(legacyKey);
        }
        return defaultValue;
    }

    private static void setBooleanIfPresent(ReadableNBT nbt, String key, BooleanConsumer consumer) {
        if (nbt.hasTag(key)) {
            consumer.accept(nbt.getBoolean(key));
        }
    }

    private static <E extends Enum<E>> void setEnumValue(Class<E> enumClass, String value, EnumConsumer<E> consumer) {
        if (value == null || value.isEmpty()) {
            return;
        }

        try {
            consumer.accept(Enum.valueOf(enumClass, value));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static String formatNumber(double value) {
        double rounded = round(value, 1);
        if (rounded == (int) value) {
            return String.valueOf((int) value);
        }
        return String.valueOf(rounded);
    }

    private static double round(double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value * scale) / scale;
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private interface EnumConsumer<E> {
        void accept(E value);
    }

    private static void setVillagerProfession(Villager villager, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        Villager.Profession profession = Registry.VILLAGER_PROFESSION.get(
                NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT))
        );
        if (profession != null) {
            villager.setProfession(profession);
        }
    }

    private enum EquipmentSlotApplier {
        MAIN_HAND {
            @Override
            void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance) {
                equipment.setItemInMainHand(itemStack);
                equipment.setItemInMainHandDropChance(dropChance);
            }
        },
        OFF_HAND {
            @Override
            void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance) {
                equipment.setItemInOffHand(itemStack);
                equipment.setItemInOffHandDropChance(dropChance);
            }
        },
        HELMET {
            @Override
            void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance) {
                equipment.setHelmet(itemStack);
                equipment.setHelmetDropChance(dropChance);
            }
        },
        CHESTPLATE {
            @Override
            void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance) {
                equipment.setChestplate(itemStack);
                equipment.setChestplateDropChance(dropChance);
            }
        },
        LEGGINGS {
            @Override
            void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance) {
                equipment.setLeggings(itemStack);
                equipment.setLeggingsDropChance(dropChance);
            }
        },
        BOOTS {
            @Override
            void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance) {
                equipment.setBoots(itemStack);
                equipment.setBootsDropChance(dropChance);
            }
        };

        abstract void apply(EntityEquipment equipment, ItemStack itemStack, float dropChance);
    }
}
