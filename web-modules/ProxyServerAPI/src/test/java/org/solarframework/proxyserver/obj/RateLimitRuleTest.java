package org.solarframework.proxyserver.obj;

import org.junit.jupiter.api.Test;
import org.solarframework.core.util.RateLimiter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitRuleTest {

    @Test
    void theCatchAllHostClaimsEveryDomain() {
        RateLimitRule rule = rule("*", "/**");
        assertTrue(rule.matches("myapp.com", "/anything"));
        assertTrue(rule.matches("api.myapp.com", "/"));
    }
    @Test
    void aNamedHostClaimsOnlyItsOwnDomain() {
        RateLimitRule rule = rule("api.myapp.com", "/**");
        assertTrue(rule.matches("API.MyApp.com", "/v1/data"));
        assertFalse(rule.matches("myapp.com", "/v1/data"));
    }
    @Test
    void aPatternClaimsOnlyThePathsBelowIt() {
        RateLimitRule rule = rule("*", "/api/**");
        assertTrue(rule.matches("myapp.com", "/api/v1/data"));
        assertFalse(rule.matches("myapp.com", "/login"));
    }


    @Test
    void callersDoNotShareABucket() {
        RateLimiter limiter = new RateLimiter();
        RateLimitRule rule = rule("*", "/**");
        assertEquals(0, rule.takePermit(limiter, "10.0.0.4"));
        assertTrue(rule.takePermit(limiter, "10.0.0.4") > 0, "the same caller has spent its only permit");
        assertEquals(0, rule.takePermit(limiter, "10.0.0.5"), "another caller starts with a full bucket");
    }
    @Test
    void twoRulesOverTheSamePathDoNotShareABucket() {
        RateLimiter limiter = new RateLimiter();
        assertEquals(0, rule("*", "/**").takePermit(limiter, "10.0.0.4"));
        assertEquals(0, rule("*", "/api/**").takePermit(limiter, "10.0.0.4"), "a different rule is a different bucket");
    }
    @Test
    void everyPathUnderOnePatternSharesOneBucket() {
        RateLimiter limiter = new RateLimiter();
        RateLimitRule rule = rule("*", "/api/**");
        assertEquals(0, rule.takePermit(limiter, "10.0.0.4"));
        assertTrue(rule.takePermit(limiter, "10.0.0.4") > 0, "the rule is limited as a whole, not per distinct URL");
    }


    @Test
    void aRateOfZeroParksTheRuleWithoutDeletingIt() {
        RateLimiter limiter = new RateLimiter();
        RateLimitRule parked = new RateLimitRule("*", "/**", 0, 1, 0, 1, 64);
        for (int i = 0; i < 50; i++) assertEquals(0, parked.takePermit(limiter, "10.0.0.4"));
    }
    @Test
    void theRowCarriesItsOwnFirstPauseIntoTheWait() {
        RateLimiter limiter = new RateLimiter();
        RateLimitRule rule = new RateLimitRule("*", "/**", 0.01, 1, 0, 5, 60);
        rule.takePermit(limiter, "10.0.0.4");
        assertEquals(5, rule.takePermit(limiter, "10.0.0.4"), "the wait is this rule's own first pause, not a fixed one");
    }


    @Test
    void aContributedRuleClaimsEveryHostAndIsMarkedAsSuch() {
        RateLimitRule rule = RateLimitRule.contributed("/api/data/v1/employers/**", 5, 20, -1, 1, 64, List.of());
        assertTrue(rule.isContributed());
        assertTrue(rule.matches("myapp.com", "/api/data/v1/employers/5"));
    }
    @Test
    void aRuleTypedByHandIsNeverMarkedAsContributed() {
        assertFalse(rule("*", "/**").isContributed());
    }


    @Test
    void noAllowedIpsMeansTheRuleRestrictsNobody() {
        assertTrue(rule("*", "/**").allows("8.8.8.8"), "a hand-typed rule never carries an allow list");
    }
    @Test
    void aContributedAllowListRefusesAnyoneNotOnIt() {
        RateLimitRule rule = RateLimitRule.contributed("/api/data/v1/employers/**", 5, 20, -1, 1, 64, List.of("10.0.0.4", "10.0.0.5"));
        assertTrue(rule.allows("10.0.0.4"));
        assertFalse(rule.allows("10.0.0.9"));
    }
    @Test
    void aContributedAllowListOfStarLetsAnyoneIn() {
        RateLimitRule rule = RateLimitRule.contributed("/api/data/v1/vacancies/**", 5, 20, -1, 1, 64, List.of("*"));
        assertTrue(rule.allows("8.8.8.8"));
    }


    /** Refills far too slowly to earn a permit back mid-test, so the second call is always the refused one. */
    private RateLimitRule rule(String host, String pathPattern) {
        return new RateLimitRule(host, pathPattern, 0.01, 1, 0, 1, 64);
    }
}
