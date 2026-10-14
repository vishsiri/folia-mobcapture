package net.johnbrooks.mh.service;

import net.johnbrooks.mh.Main;
import org.bukkit.event.Event;

public final class PluginEventBus {
    public void call(Event event) {
        Main.plugin.getServer().getPluginManager().callEvent(event);
    }
}
