package net.johnbrooks.mh.compat;

import net.johnbrooks.mh.Main;
import org.bukkit.entity.Entity;

public final class Scheduler {
    private Scheduler() {
    }

    public static void runEntityTaskLater(Entity entity, Runnable task, long delay) {
        entity.getScheduler().runDelayed(Main.plugin, scheduledTask -> task.run(), null, delay);
    }
}
