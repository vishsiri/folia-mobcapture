package net.johnbrooks.mh.event.custom;

import lombok.Getter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CreatureCaptureEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;
    @Getter
    private Player captor;
    @Getter
    private LivingEntity targetEntity;

    public CreatureCaptureEvent(Player captor, LivingEntity targetEntity) {
        this.cancelled = false;
        this.captor = captor;
        this.targetEntity = targetEntity;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
