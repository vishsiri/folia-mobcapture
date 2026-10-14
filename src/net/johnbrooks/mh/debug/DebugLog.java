package net.johnbrooks.mh.debug;

import net.johnbrooks.mh.Main;
import net.johnbrooks.mh.config.Settings;
import org.bukkit.Location;

public final class DebugLog {
    private DebugLog() {
    }

    public static boolean isEnabled() {
        return Settings.debugMode;
    }

    public static void info(String message) {
        if (Settings.debugMode && Main.logger != null) {
            Main.logger.info("[Debug] " + message);
        }
    }

    public static String location(Location location) {
        if (location == null) {
            return "unknown";
        }
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        int blockX = location.getBlockX();
        int blockZ = location.getBlockZ();
        return world + " " + blockX + "," + location.getBlockY() + "," + blockZ
                + " (chunk " + (blockX >> 4) + "," + (blockZ >> 4) + ")";
    }
}
