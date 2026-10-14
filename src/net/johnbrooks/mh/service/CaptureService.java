package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.compat.WorldGuardHook;
import net.johnbrooks.mh.debug.DebugLog;
import net.johnbrooks.mh.i18n.Message;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.event.custom.CreatureCaptureEvent;
import net.johnbrooks.mh.domain.item.CaptureEgg;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public final class CaptureService {
    private final PluginEventBus eventBus;
    private final CaptureTitleAnimationService titleAnimationService;

    public CaptureService(PluginEventBus eventBus) {
        this.eventBus = eventBus;
        this.titleAnimationService = new CaptureTitleAnimationService();
    }

    public boolean attemptCapture(Player catcher, LivingEntity target) {
        return attemptCapture(catcher, target, 100D);
    }

    public boolean attemptCapture(Player catcher, LivingEntity target, double chance) {
        return attemptCapture(catcher, target, chance, CaptureItemUse.none());
    }

    public boolean attemptCapture(Player catcher, LivingEntity target, double chance, CaptureItemUse captureItemUse) {
        if (DebugLog.isEnabled()) {
            DebugLog.info("Capture attempt: player=" + catcher.getName()
                    + ", entity=" + target.getType().name()
                    + ", uuid=" + target.getUniqueId()
                    + ", chance=" + formatChance(chance) + "%"
                    + ", location=" + DebugLog.location(target.getLocation()));
        }

        if (Settings.isBlacklistedEntity(target)) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Capture denied: entity is blacklisted (" + target.getType().name() + ").");
            }
            Message.sendKey(catcher, "capture-blacklisted-entity", "entity", target.getType().name());
            return false;
        }

        if (!Main.permissionService.hasPermissionToCapture(catcher, target)) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Capture denied: player lacks capture permission (player=" + catcher.getName() + ").");
            }
            Message.sendKey(catcher, "no-permission-capture");
            return false;
        }

        if (cannotUseProtectedArea(catcher, target)) {
            Message.sendKey(catcher, "no-permission-area");
            return false;
        }

        if (Settings.isDisabledWorld(catcher.getWorld().getName())) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Capture denied: world is disabled (world=" + catcher.getWorld().getName() + ").");
            }
            Message.sendKey(catcher, "capture-disabled-world");
            return false;
        }

        CreatureCaptureEvent captureEvent = new CreatureCaptureEvent(catcher, target);
        eventBus.call(captureEvent);
        if (captureEvent.isCancelled()) {
            DebugLog.info("Capture denied: CreatureCaptureEvent was cancelled by a listener.");
            return false;
        }

        CaptureTitleAnimationService.Reservation animationReservation = null;
        if (Settings.captureAnimationEnabled) {
            animationReservation = titleAnimationService.reserve(catcher, target);
            if (animationReservation == null) {
                DebugLog.info("Capture denied: player or entity already has a capture animation in progress.");
                Message.sendKey(catcher, "capture-already-in-progress");
                return true;
            }
        }

        if (!captureItemUse.consumeOnAttempt()) {
            titleAnimationService.release(animationReservation);
            DebugLog.info("Capture denied: required capture item could not be consumed on attempt.");
            return false;
        }

        if (!rollCaptureChance(chance)) {
            if (DebugLog.isEnabled()) {
                DebugLog.info("Capture failed: chance roll did not pass (chance=" + formatChance(chance) + "%).");
            }
            if (animationReservation != null && titleAnimationService.play(
                    animationReservation,
                    chance,
                    false,
                    () -> false,
                    result -> Message.sendKey(catcher, "capture-failed-chance", "chance", formatChance(chance)))) {
                return true;
            }
            titleAnimationService.release(animationReservation);
            Message.sendKey(catcher, "capture-failed-chance", "chance", formatChance(chance));
            return false;
        }

        if (!captureItemUse.consumeOnSuccess()) {
            titleAnimationService.release(animationReservation);
            DebugLog.info("Capture denied: required capture item could not be consumed on success.");
            return false;
        }

        if (!chargeCaptureCost(catcher)) {
            titleAnimationService.release(animationReservation);
            if (DebugLog.isEnabled()) {
                DebugLog.info("Capture denied: configured cost could not be charged (mode=" + Settings.costMode + ").");
            }
            return false;
        }

        LivingEntity captureTarget = captureEvent.getTargetEntity();
        if (animationReservation != null && titleAnimationService.play(
                animationReservation,
                chance,
                true,
                () -> completeCapture(captureTarget),
                result -> handleAnimatedSuccessResult(catcher, captureTarget, result))) {
            return true;
        }
        titleAnimationService.release(animationReservation);

        boolean captured = completeCapture(captureTarget);
        if (captured) {
            logCaptureSuccess(catcher, captureTarget);
        } else {
            Message.sendKey(catcher, "capture-interrupted");
        }
        return captured;
    }

    private boolean completeCapture(LivingEntity target) {
        if (target == null || !target.isValid() || target.isDead()) {
            return false;
        }
        if (!CaptureEgg.captureLivingEntity(target)) {
            DebugLog.info("Capture cancelled: a complete entity snapshot could not be stored.");
            return false;
        }
        target.remove();
        return true;
    }

    private void handleAnimatedSuccessResult(Player catcher, LivingEntity target, boolean captured) {
        if (captured) {
            logCaptureSuccess(catcher, target);
            return;
        }
        if (DebugLog.isEnabled()) {
            DebugLog.info("Capture interrupted before completion: player=" + catcher.getName()
                    + ", entity=" + target.getType().name() + ", uuid=" + target.getUniqueId() + ".");
        }
        Message.sendKey(catcher, "capture-interrupted");
    }

    private void logCaptureSuccess(Player catcher, LivingEntity target) {
        if (DebugLog.isEnabled()) {
            DebugLog.info("Capture succeeded: player=" + catcher.getName() + ", entity=" + target.getType().name()
                    + ", uuid=" + target.getUniqueId() + ".");
        }
    }

    private boolean cannotUseProtectedArea(Player catcher, LivingEntity target) {
        return cannotBuildInGriefPreventionClaim(catcher, target)
                || cannotBuildInWorldGuardRegion(catcher, target);
    }

    private boolean cannotBuildInGriefPreventionClaim(Player catcher, LivingEntity target) {
        if (!Settings.griefPreventionHook) {
            return false;
        }
        if (Main.griefPrevention == null) {
            DebugLog.info("Capture denied by GriefPrevention hook: plugin instance is unavailable.");
            return true;
        }
        boolean claimsEnabled = Main.griefPrevention.claimsEnabledForWorld(target.getWorld());
        boolean denied = claimsEnabled && Main.griefPrevention.allowBuild(catcher, target.getLocation()) != null;
        if (DebugLog.isEnabled()) {
            DebugLog.info("GriefPrevention capture check: player=" + catcher.getName()
                    + ", claims-enabled=" + claimsEnabled + ", allowed=" + !denied + ".");
        }
        return denied;
    }

    private boolean cannotBuildInWorldGuardRegion(Player catcher, LivingEntity target) {
        if (!Settings.worldGuardHook) {
            return false;
        }
        boolean allowed = WorldGuardHook.canBuild(catcher, target.getLocation());
        if (DebugLog.isEnabled()) {
            DebugLog.info("WorldGuard capture check: player=" + catcher.getName()
                    + ", build=" + allowed + ", location=" + DebugLog.location(target.getLocation()) + ".");
        }
        return !allowed;
    }

    private boolean chargeCaptureCost(Player catcher) {
        if (catcher.hasPermission(Main.permissionService.NoCost) || Settings.costMode == Settings.CostMode.NONE) {
            return true;
        }

        if (EconomyService.chargePlayer(catcher)) {
            return true;
        }

        Message.sendKey(catcher, "not-enough-cost", "cost", Settings.costMode == Settings.CostMode.ITEM
                ? Settings.costMaterial.name()
                : "money (" + Settings.costVault + " required)");
        return false;
    }

    private boolean rollCaptureChance(double chance) {
        if (chance >= 100D) {
            return true;
        }
        if (chance <= 0D) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100D) < chance;
    }

    private String formatChance(double chance) {
        double rounded = Math.round(chance * 10D) / 10D;
        return rounded == (int) rounded ? String.valueOf((int) rounded) : String.valueOf(rounded);
    }

    public interface CaptureItemUse {
        boolean consumeOnAttempt();

        boolean consumeOnSuccess();

        static CaptureItemUse none() {
            return new CaptureItemUse() {
                @Override
                public boolean consumeOnAttempt() {
                    return true;
                }

                @Override
                public boolean consumeOnSuccess() {
                    return true;
                }
            };
        }
    }
}
