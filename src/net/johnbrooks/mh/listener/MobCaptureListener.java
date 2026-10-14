package net.johnbrooks.mh.listener;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.domain.item.CaptureItemConsumeMode;
import net.johnbrooks.mh.i18n.Message;
import net.johnbrooks.mh.persistence.NBTManager;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.service.CaptureService;
import net.johnbrooks.mh.service.ChickenSpawnService;
import net.johnbrooks.mh.service.PluginEventBus;
import net.johnbrooks.mh.service.ProjectileService;
import net.johnbrooks.mh.service.SpawnEggReleaseService;
import org.bukkit.entity.Egg;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class MobCaptureListener implements Listener {
    private final ProjectileService projectileService;
    private final CaptureService captureService;
    private final SpawnEggReleaseService releaseService;
    private final ChickenSpawnService chickenSpawnService;

    public MobCaptureListener() {
        PluginEventBus eventBus = new PluginEventBus();
        this.projectileService = new ProjectileService();
        this.captureService = new CaptureService(eventBus);
        this.releaseService = new SpawnEggReleaseService(eventBus);
        this.chickenSpawnService = new ChickenSpawnService();
    }

    public void initialize() {
        register(this);
        register(new TownyCaptureListener());
    }

    private void register(Listener listener) {
        Main.plugin.getServer().getPluginManager().registerEvents(listener, Main.plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void assignMetaDataOnLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            projectileService.markIfCaptureProjectile(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChickenSpawn(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg)) {
            return;
        }

        if (projectileService.isCaptureProjectile(event.getEntity()) && event.getHitEntity() != null) {
            return;
        }

        chickenSpawnService.spawnEggChickens(event.getEntity().getLocation());
    }

    @EventHandler
    public void onVanillaChickenSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG && event.getEntity() instanceof org.bukkit.entity.Chicken) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void captureEvent(EntityDamageByEntityEvent event) {
        Player player = resolveCaptor(event);
        if (player == null) {
            return;
        }

        boolean success = captureService.attemptCapture(
                player,
                (LivingEntity) event.getEntity(),
                resolveCaptureChance(event),
                captureItemUse(event.getDamager() instanceof Projectile, player)
        );
        if (success) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preventUseEggOnOtherEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (isHeldCaptureEgg(player)) {
            event.setCancelled(true);
            return;
        }

        if (!projectileService.isCaptureItem(heldItem)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getRightClicked() instanceof LivingEntity) || event.getRightClicked() instanceof Player) {
            return;
        }

        boolean success = captureService.attemptCapture(
                player,
                (LivingEntity) event.getRightClicked(),
                projectileService.getCaptureChance(heldItem),
                captureItemUse(false, player)
        );
        if (success) {
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void useSpawnEgg(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            projectileService.prepareCaptureProjectile(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand());
        }

        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !isHeldCaptureEgg(event.getPlayer())) {
            return;
        }

        Player player = event.getPlayer();
        boolean canUseInteractedBlock = event.useInteractedBlock() != Event.Result.DENY;
        event.setCancelled(true);
        if (!releaseService.canReleaseInWorld(player)) {
            return;
        }

        ItemStack spawnItem = player.getInventory().getItemInMainHand();
        if (canUseInteractedBlock && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            releaseService.releaseAtBlock(player, spawnItem, event.getClickedBlock().getLocation(), event.getBlockFace());
        }
    }

    private Player resolveCaptor(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return null;
        }

        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectileService.isCaptureProjectile(projectile) && projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }

        if (Settings.meleeCapture && event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (projectileService.isCaptureItem(heldItem)) {
                return player;
            }
        }

        return null;
    }

    private double resolveCaptureChance(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile) {
            return projectileService.getCaptureChance((Projectile) event.getDamager());
        }
        if (event.getDamager() instanceof Player) {
            return projectileService.getCaptureChance(((Player) event.getDamager()).getInventory().getItemInMainHand());
        }
        return 100D;
    }

    private CaptureService.CaptureItemUse captureItemUse(boolean projectileCapture, Player player) {
        return new CaptureService.CaptureItemUse() {
            @Override
            public boolean consumeOnAttempt() {
                if (Settings.captureItemConsumeMode != CaptureItemConsumeMode.ON_ATTEMPT || projectileCapture) {
                    return true;
                }
                return consume(player);
            }

            @Override
            public boolean consumeOnSuccess() {
                if (Settings.captureItemConsumeMode != CaptureItemConsumeMode.ON_SUCCESS) {
                    return true;
                }
                return consume(player);
            }
        };
    }

    private boolean consume(Player player) {
        if (projectileService.consumeCaptureItem(player)) {
            return true;
        }

        Message.sendKey(player, "capture-item-required");
        return false;
    }

    private boolean isHeldCaptureEgg(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        return heldItem != null && NBTManager.isSpawnEgg(heldItem);
    }
}
