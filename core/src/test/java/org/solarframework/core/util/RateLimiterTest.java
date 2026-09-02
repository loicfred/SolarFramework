package org.solarframework.core.util;

import org.junit.jupiter.api.Test;
import org.solarframework.core.util.RateLimiter.Limit;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);
    /** 2 permits a second, 3 to start with, no pause on top. */
    private static final Limit PLAIN = new Limit(2, 3, 0, 0);
    /** Refills far too slowly to earn a permit mid-pause, so the escalation is what the tests are watching. */
    private static final Limit ESCALATING = new Limit(0.01, 1, 1, 8);

    @Test
    void burstIsSpentThenTheNextCallIsRefused() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) assertTrue(limiter.tryConsume("a", PLAIN, 0), "call " + i + " should pass");
        assertFalse(limiter.tryConsume("a", PLAIN, 0));
    }
    @Test
    void oneSecondLaterExactlyThatManyMorePass() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, PLAIN, 0);
        assertTrue(limiter.tryConsume("a", PLAIN, SECOND));
        assertTrue(limiter.tryConsume("a", PLAIN, SECOND));
        assertFalse(limiter.tryConsume("a", PLAIN, SECOND));
    }
    @Test
    void idleTimeDoesNotAccumulateBeyondBurst() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryConsume("a", PLAIN, 0);
        long anHourLater = TimeUnit.HOURS.toNanos(1);
        for (int i = 0; i < 3; i++) assertTrue(limiter.tryConsume("a", PLAIN, anHourLater));
        assertFalse(limiter.tryConsume("a", PLAIN, anHourLater));
    }


    @Test
    void aRateOfZeroOrLessNeverLimits() {
        RateLimiter limiter = new RateLimiter();
        Limit parked = new Limit(0, 1, 1, 8);
        for (int i = 0; i < 100; i++) assertTrue(limiter.tryConsume("a", parked, 0));
        assertEquals(0, limiter.size(), "a parked rule should not allocate a bucket");
    }
    @Test
    void keysDoNotShareABucket() {
        RateLimiter limiter = new RateLimiter();
        Limit one = new Limit(1, 1, 0, 0);
        assertTrue(limiter.tryConsume("a", one, 0));
        assertFalse(limiter.tryConsume("a", one, 0));
        assertTrue(limiter.tryConsume("b", one, 0));
    }


    @Test
    void theFirstRefusalPausesForTheConfiguredFirstPause() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, ESCALATING, 0);
        assertFalse(limiter.tryConsume("a", ESCALATING, 0));
        assertEquals(1, limiter.retryAfterSeconds("a", ESCALATING, 0));
    }
    @Test
    void knockingDuringThePauseDoublesTheNextOne() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, ESCALATING, 0);
        limiter.tryConsume("a", ESCALATING, 0);                     // refused, a 1s pause starts
        limiter.tryConsume("a", ESCALATING, SECOND / 2);            // knocked while paused

        limiter.tryConsume("a", ESCALATING, SECOND);                // pause over, still no permit
        assertEquals(2, limiter.retryAfterSeconds("a", ESCALATING, SECOND));
    }
    @Test
    void thePauseDoublesOncePerWindowNotOncePerRequest() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, ESCALATING, 0);
        limiter.tryConsume("a", ESCALATING, 0);
        for (int i = 0; i < 20; i++) limiter.tryConsume("a", ESCALATING, SECOND / 2);

        limiter.tryConsume("a", ESCALATING, SECOND);
        assertEquals(2, limiter.retryAfterSeconds("a", ESCALATING, SECOND), "twenty parallel requests are one offence, not twenty");
    }
    @Test
    void thePauseKeepsDoublingUpToTheCeiling() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, ESCALATING, 0);
        long now = 0;
        for (long expected : new long[]{1, 2, 4, 8, 8, 8}) {
            limiter.tryConsume("a", ESCALATING, now);               // refused, a pause of `expected` starts
            assertEquals(expected, limiter.retryAfterSeconds("a", ESCALATING, now));
            now += TimeUnit.SECONDS.toNanos(expected) / 2;
            limiter.tryConsume("a", ESCALATING, now);               // knock while paused
            now += TimeUnit.SECONDS.toNanos(expected);
        }
    }
    @Test
    void backingOffDuringThePauseDoesNotDoubleIt() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, ESCALATING, 0);
        limiter.tryConsume("a", ESCALATING, 0);                     // refused, a 1s pause, nobody knocks

        limiter.tryConsume("a", ESCALATING, SECOND);
        assertEquals(1, limiter.retryAfterSeconds("a", ESCALATING, SECOND), "a caller that waited is not punished harder");
    }
    @Test
    void stayingAwayAsLongAsThePauseWipesTheSlate() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, ESCALATING, 0);
        limiter.tryConsume("a", ESCALATING, 0);
        limiter.tryConsume("a", ESCALATING, SECOND / 2);            // escalates the next one to 2s
        limiter.tryConsume("a", ESCALATING, SECOND);
        assertEquals(2, limiter.retryAfterSeconds("a", ESCALATING, SECOND));

        long muchLater = TimeUnit.MINUTES.toNanos(5);
        spend(limiter, ESCALATING, muchLater);
        limiter.tryConsume("a", ESCALATING, muchLater);
        assertEquals(1, limiter.retryAfterSeconds("a", ESCALATING, muchLater), "a caller that went quiet starts again at the first pause");
    }
    @Test
    void aFirstPauseOfZeroLeavesThePlainBucketBehaviour() {
        RateLimiter limiter = new RateLimiter();
        spend(limiter, PLAIN, 0);
        limiter.tryConsume("a", PLAIN, 0);
        assertTrue(limiter.tryConsume("a", PLAIN, SECOND), "without a pause the permit is usable as soon as it is earned");
    }
    @Test
    void aCeilingBelowTheFirstPauseMeansAFlatPause() {
        RateLimiter limiter = new RateLimiter();
        Limit flat = new Limit(0.01, 1, 5, 0);
        spend(limiter, flat, 0);
        limiter.tryConsume("a", flat, 0);
        assertEquals(5, limiter.retryAfterSeconds("a", flat, 0));

        limiter.tryConsume("a", flat, TimeUnit.SECONDS.toNanos(2));
        limiter.tryConsume("a", flat, TimeUnit.SECONDS.toNanos(5));
        assertEquals(5, limiter.retryAfterSeconds("a", flat, TimeUnit.SECONDS.toNanos(5)), "no ceiling above the first pause means no doubling");
    }


    @Test
    void retryAfterOfAnUnknownKeyIsTheFirstPause() {
        assertEquals(1, new RateLimiter().retryAfterSeconds("never-seen", ESCALATING, 0));
    }
    @Test
    void theSweepDropsIdleBucketsAndKeepsActiveOnes() {
        RateLimiter limiter = new RateLimiter();
        long elevenMinutes = TimeUnit.MINUTES.toNanos(11);
        limiter.tryConsume("idle", PLAIN, 0);
        limiter.tryConsume("active", PLAIN, elevenMinutes);

        limiter.sweepNow(elevenMinutes);
        assertEquals(1, limiter.size());
    }
    @Test
    void aBucketStillServingAPauseIsNeverSwept() {
        RateLimiter limiter = new RateLimiter();
        Limit longPause = new Limit(0.01, 1, 3600, 3600);
        limiter.tryConsume("a", longPause, 0);
        limiter.tryConsume("a", longPause, 0);                      // refused, an hour-long pause starts

        limiter.sweepNow(TimeUnit.MINUTES.toNanos(11));
        assertEquals(1, limiter.size(), "sweeping a paused caller would forgive it for using memory");
    }
    @Test
    void takePermitAnswersZeroWhileThereArePermitsThenTheWait() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) assertEquals(0, PLAIN.takePermit(limiter, "a", 0), "call " + i + " should go ahead");
        assertTrue(PLAIN.takePermit(limiter, "a", 0) > 0, "the fourth call is refused and must name a wait");
    }
    @Test
    void takePermitNeverRefusesAParkedLimit() {
        RateLimiter limiter = new RateLimiter();
        Limit parked = new Limit(0, 1, 1, 8);
        for (int i = 0; i < 100; i++) assertEquals(0, parked.takePermit(limiter, "a", 0));
    }


    @Test
    void clearForgetsEveryCaller() {
        RateLimiter limiter = new RateLimiter();
        limiter.tryConsume("a", PLAIN, 0);
        limiter.clear();
        assertEquals(0, limiter.size());
        assertTrue(limiter.tryConsume("a", PLAIN, 0));
    }


    /** Empties the bucket so the next call is the one that gets refused. */
    private void spend(RateLimiter limiter, Limit limit, long nowNanos) {
        for (int i = 0; i < limit.burst(); i++) limiter.tryConsume("a", limit, nowNanos);
    }
}
