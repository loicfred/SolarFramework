package org.solarframework.tournament.util;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Epoch-millis identifiers, monotonic within the JVM.
 * Bracket generation creates hundreds of rows in the same millisecond, so a plain
 * {@code Instant.now().toEpochMilli()} would collide - this never hands out the same value twice.
 */
public final class Ids {
    private static final AtomicLong LAST = new AtomicLong(0);
    private Ids() {}

    public static long next() {
        long now = Instant.now().toEpochMilli();
        return LAST.updateAndGet(p -> Math.max(now, p + 1));
    }
}
