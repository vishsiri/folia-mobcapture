package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

public class PermissionService {
    public final String NoCost = Main.plugin.getName() + ".NoCost";
    public final String CatchPrefix = Main.plugin.getName() + ".Catch.";
    public final String CatchUse = "MobCapture.Catch.Use";
    public final String CatchPeaceful = "MobCapture.Catch.Peaceful";
    public final String CatchHostile = "MobCapture.Catch.Hostile";

    public boolean hasPermissionToCapture(Player player, LivingEntity livingEntity) {
        if (player.hasPermission(CatchUse)) {
            return true;
        } else if (livingEntity instanceof Monster && player.hasPermission(CatchHostile)) {
            return true;
        } else if (livingEntity instanceof Mob && player.hasPermission(CatchPeaceful)) {
            return true;
        } else {
            return player.hasPermission(CatchPrefix + livingEntity.getType().name());
        }
    }
}
