package me.allync.blockregen.api.event;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class BlockRegenRegenerateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final BlockState originalState;
    private final String blockIdentifier;
    private final String regenVariantIdentifier;

    public BlockRegenRegenerateEvent(BlockState originalState, String blockIdentifier, String regenVariantIdentifier) {
        this.originalState = originalState;
        this.blockIdentifier = blockIdentifier;
        this.regenVariantIdentifier = regenVariantIdentifier;
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public Location getLocation() {
        return originalState.getLocation();
    }

    public String getBlockIdentifier() {
        return blockIdentifier;
    }

    public String getRegenVariantIdentifier() {
        return regenVariantIdentifier;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
