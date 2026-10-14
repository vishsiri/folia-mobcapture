package net.johnbrooks.mh.listener;

import net.johnbrooks.mh.compat.TownyHook;
import net.johnbrooks.mh.i18n.Message;
import net.johnbrooks.mh.config.Settings;
import net.johnbrooks.mh.debug.DebugLog;
import net.johnbrooks.mh.event.custom.CreatureCaptureEvent;
import net.johnbrooks.mh.event.custom.CreatureReleaseEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TownyCaptureListener implements Listener {
    @EventHandler
    public void townyCheck(CreatureCaptureEvent event) {
        if (event.isCancelled() || !Settings.townyHook) {
            return;
        }

        boolean allowed = TownyHook.canUse(event.getCaptor(), event.getTargetEntity().getLocation());
        if (DebugLog.isEnabled()) {
            DebugLog.info("Towny capture check: player=" + event.getCaptor().getName()
                    + ", allowed=" + allowed
                    + ", location=" + DebugLog.location(event.getTargetEntity().getLocation()) + ".");
        }
        if (!allowed) {
            Message.sendKey(event.getCaptor(), "no-permission-area");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void townyReleaseCheck(CreatureReleaseEvent event) {
        if (event.isCancelled() || !Settings.townyHook || !Settings.releaseProtection) {
            return;
        }

        Location target = event.getTarget();
        boolean allowed = TownyHook.canUse(event.getUser(), target);
        if (DebugLog.isEnabled()) {
            DebugLog.info("Towny release check: player=" + event.getUser().getName()
                    + ", allowed=" + allowed + ", location=" + DebugLog.location(target) + ".");
        }
        if (!allowed) {
            Message.sendKey(event.getUser(), "no-permission-area");
            event.setCancelled(true);
        }
    }
}
