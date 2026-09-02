package org.solarframework.core.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Token bucket store: every caller holds a bucket that refills at a fixed rate and never holds more than its burst size.
 *  A caller that keeps knocking while it is being refused serves a pause that doubles each time, so spamming gets
 *  progressively more expensive while a caller that backs off never waits longer than the first pause. */
public class RateLimiter {
    /** Past this many live buckets the idle ones are swept, so a flood of one-off callers cannot grow the map without bound. */
    private static final int SWEEP_ABOVE = 20_000;
    /** A bucket untouched this long has refilled to capacity, so dropping it is identical to never having seen the caller. */
    private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final long SWEEP_EVERY_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastSweepNanos = System.nanoTime();


    /** What one caller is allowed. Carried in on every call rather than stored, so an edited limit applies to the very next request. */
    public record Limit(double permitsPerSecond, int burst, long firstPauseSeconds, long maxPauseSeconds) {
        /** A rate of zero is how a rule is parked without being deleted. */
        public boolean isUnlimited() {
            return permitsPerSecond <= 0;
        }
        /** A first pause of zero leaves the plain bucket behaviour: refused until a permit exists, with no pause on top. */
        public boolean hasPause() {
            return firstPauseSeconds > 0;
        }
        /** The pause that follows one of {@code served} seconds - the first, the same again, or double it, never past the ceiling. */
        public long nextPauseSeconds(long served, boolean escalate) {
            if (served == 0) return firstPauseSeconds;
            return escalate ? Math.min(Math.max(maxPauseSeconds, firstPauseSeconds), served * 2) : served;
        }
        /** Spends one of this caller's permits and answers the seconds it must wait; 0 lets the request go ahead.
         *  Lives here so the edge and the application both refuse a caller on the same terms rather than each writing the pair of calls. */
        public long takePermit(RateLimiter limiter, String key, long nowNanos) {
            return limiter.tryConsume(key, this, nowNanos) ? 0 : limiter.retryAfterSeconds(key, this, nowNanos);
        }
    }


    /** Takes one permit for the caller, or refuses when the bucket is empty or a pause is still running.
     *  The clock is a parameter so the caller can decide one instant for the whole check, and so the escalation can be tested without sleeping. */
    public boolean tryConsume(String key, Limit limit, long nowNanos) {
        if (limit.isUnlimited()) return true;
        sweepIdleBuckets(nowNanos);
        return buckets.computeIfAbsent(key, k -> new Bucket(limit.burst(), nowNanos)).tryConsume(limit, nowNanos);
    }

    /** Whole seconds the caller must wait, for the Retry-After header: the rest of its pause, or the time to earn one permit. */
    public long retryAfterSeconds(String key, Limit limit, long nowNanos) {
        Bucket bucket = buckets.get(key);
        return bucket == null ? Math.max(1, limit.firstPauseSeconds()) : bucket.retryAfterSeconds(limit, nowNanos);
    }

    /** Exposed for the tests, which are the only thing that needs to count live callers. */
    int size() {
        return buckets.size();
    }
    public void clear() {
        buckets.clear();
    }


    /** Runs at most once a minute and only once the map is large, so the common path stays a single lookup. */
    private void sweepIdleBuckets(long nowNanos) {
        if (buckets.size() < SWEEP_ABOVE || nowNanos - lastSweepNanos < SWEEP_EVERY_NANOS) return;
        lastSweepNanos = nowNanos;
        sweepNow(nowNanos);
    }
    /** Exposed for the tests, which cannot wait a minute to watch eviction happen. */
    void sweepNow(long nowNanos) {
        buckets.values().removeIf(bucket -> bucket.isIdleSince(nowNanos));
    }


    /** One caller's permits and its standing with the limiter. */
    private static final class Bucket {
        private double tokens;
        private long lastNanos;
        private long pauseUntilNanos;
        private long pauseSeconds;
        private boolean knockedDuringPause;

        private Bucket(int burst, long nowNanos) {
            this.tokens = burst;
            this.lastNanos = nowNanos;
        }

        private synchronized boolean tryConsume(Limit limit, long nowNanos) {
            if (nowNanos < pauseUntilNanos) {
                knockedDuringPause = true;
                return false;
            }
            forgiveIfCleanSincePause(limit, nowNanos);
            // refilled on read rather than by a timer, so an idle bucket costs nothing until the caller comes back
            tokens = Math.min(limit.burst(), tokens + (nowNanos - lastNanos) / 1_000_000_000d * limit.permitsPerSecond());
            lastNanos = nowNanos;
            if (tokens < 1) {
                startOrDoublePause(limit, nowNanos);
                return false;
            }
            tokens--;
            return true;
        }
        /** Doubles only when the caller kept knocking through the last pause, so a page firing twenty parallel requests escalates once rather than twenty times. */
        private void startOrDoublePause(Limit limit, long nowNanos) {
            if (!limit.hasPause()) return;
            pauseSeconds = limit.nextPauseSeconds(pauseSeconds, knockedDuringPause);
            knockedDuringPause = false;
            pauseUntilNanos = nowNanos + TimeUnit.SECONDS.toNanos(pauseSeconds);
        }
        /** The slate is wiped once the caller has stayed away for as long as the pause it last served, so backing off is what earns forgiveness. */
        private void forgiveIfCleanSincePause(Limit limit, long nowNanos) {
            if (pauseSeconds == 0 || nowNanos - pauseUntilNanos < TimeUnit.SECONDS.toNanos(pauseSeconds)) return;
            pauseSeconds = 0;
            knockedDuringPause = false;
        }

        private synchronized long retryAfterSeconds(Limit limit, long nowNanos) {
            if (nowNanos < pauseUntilNanos) return Math.max(1, (long) Math.ceil((pauseUntilNanos - nowNanos) / 1_000_000_000d));
            return Math.max(1, (long) Math.ceil((1 - tokens) / limit.permitsPerSecond()));
        }
        /** A bucket still serving a pause is never swept, or the caller would be forgiven by running the proxy out of memory. */
        private synchronized boolean isIdleSince(long nowNanos) {
            return nowNanos - lastNanos > IDLE_NANOS && nowNanos >= pauseUntilNanos;
        }
    }
}
