package me.allync.blockregen.data;

/**
 * Stores persisted mining progress for a block so another player can continue it.
 */
public class MiningProgressState {

    private final long elapsedMs;
    private final long updatedAtMs;

    public MiningProgressState(long elapsedMs, long updatedAtMs) {
        this.elapsedMs = Math.max(0L, elapsedMs);
        this.updatedAtMs = updatedAtMs;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public long getUpdatedAtMs() {
        return updatedAtMs;
    }
}

