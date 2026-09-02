package org.solarframework.proxyserver.obj;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.solarframework.core.util.IpUtils;
import org.solarframework.core.util.RateLimiter;
import org.solarframework.db.spring.DatabaseObject;
import org.springframework.util.AntPathMatcher;

import java.time.Instant;
import java.util.List;

/** One request limit. A "*" host limits every domain the proxy serves, a named host limits only that one. */
@Entity
@Table(name = "web_ratelimit")
public class RateLimitRule extends DatabaseObject.ID_OBJ<Long, RateLimitRule> {
    public static final String ALL_HOSTS = "*";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Column(name = "Host", nullable = false, length = 128)
    private String host;
    @Column(name = "PathPattern", nullable = false, length = 128)
    private String pathPattern;
    @Column(name = "Permits", nullable = false)
    private double permits;
    @Column(name = "Burst", nullable = false)
    private int burst;
    @Column(name = "Ordering", nullable = false)
    private int ordering;
    @Column(name = "FirstPause", nullable = false)
    private long firstPauseSeconds;
    @Column(name = "MaxPause", nullable = false)
    private long maxPauseSeconds;
    /**
     * Built fresh on every reload from another module's own screen rather than typed in here, so it is never
     * persisted. The edge stamps a forwarded call as already checked when the matched rule carries this, so the
     * application behind it spends the caller's permit only once.
     */
    @Transient
    private boolean contributed = false;
    /** Also built fresh, also never persisted - who may call at all, separate from how often. An admin's own row
     *  never sets this, so it never restricts a caller by address; only a contributed rule does. */
    @Transient
    private List<String> allowedIps = List.of();


    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }

    public String getPathPattern() {
        return pathPattern;
    }
    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }

    public double getPermits() {
        return permits;
    }
    public void setPermits(double permits) {
        this.permits = permits;
    }

    public int getBurst() {
        return burst;
    }
    public void setBurst(int burst) {
        this.burst = burst;
    }

    public int getOrdering() {
        return ordering;
    }
    public void setOrdering(int ordering) {
        this.ordering = ordering;
    }

    public long getFirstPauseSeconds() {
        return firstPauseSeconds;
    }
    public void setFirstPauseSeconds(long firstPauseSeconds) {
        this.firstPauseSeconds = firstPauseSeconds;
    }

    public long getMaxPauseSeconds() {
        return maxPauseSeconds;
    }
    public void setMaxPauseSeconds(long maxPauseSeconds) {
        this.maxPauseSeconds = maxPauseSeconds;
    }

    public boolean isContributed() {
        return contributed;
    }
    public List<String> getAllowedIps() {
        return allowedIps;
    }


    protected RateLimitRule() {}
    public RateLimitRule(String host, String pathPattern, double permits, int burst, int ordering, long firstPauseSeconds, long maxPauseSeconds) {
        this.ID = Instant.now().toEpochMilli();
        this.host = host;
        this.pathPattern = pathPattern;
        this.permits = permits;
        this.burst = burst;
        this.ordering = ordering;
        this.firstPauseSeconds = firstPauseSeconds;
        this.maxPauseSeconds = maxPauseSeconds;
    }
    /**
     * A rule built fresh from another module's own saved limit and allow list rather than typed into this screen -
     * {@code ordering} still has to be given, so several contributed rules sort the same deterministic way on
     * every reload.
     */
    public static RateLimitRule contributed(String pathPattern, double permits, int burst, int ordering, long firstPauseSeconds, long maxPauseSeconds, List<String> allowedIps) {
        RateLimitRule rule = new RateLimitRule(ALL_HOSTS, pathPattern, permits, burst, ordering, firstPauseSeconds, maxPauseSeconds);
        rule.contributed = true;
        rule.allowedIps = allowedIps == null ? List.of() : allowedIps;
        return rule;
    }


    /** A rule claims a request when its host matches - or is the catch-all - and its pattern matches the path. */
    public boolean matches(String host, String path) {
        return (ALL_HOSTS.equals(this.host) || this.host.equalsIgnoreCase(host)) && MATCHER.match(pathPattern, path);
    }
    /** Whether this rule lets the caller in at all, checked before a permit is ever spent - a flood from an address
     *  nobody allowed should not cost the caller, or the bucket, anything. No list means this rule restricts nobody. */
    public boolean allows(String clientIp) {
        return allowedIps.isEmpty() || IpUtils.matches(allowedIps, clientIp);
    }
    /** Spends one of this caller's permits against this rule and answers the seconds it must wait; 0 lets the request go ahead.
     *  A rate of zero is how an admin parks a rule without deleting it, and the limiter lets those through untouched. */
    public long takePermit(RateLimiter limiter, String clientIp) {
        RateLimiter.Limit limit = new RateLimiter.Limit(permits, burst, firstPauseSeconds, maxPauseSeconds);
        // every request this rule claims shares one bucket, so the endpoint is limited as a whole rather than each distinct URL
        return limit.takePermit(limiter, clientIp + "|" + host + "|" + pathPattern, System.nanoTime());
    }
}
