package org.solarframework.core.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.solarframework.core.util.StringUtils.trimLower;

/** Matches a caller's address against an allow list of plain text entries: "*", "localhost", or literal addresses. */
public class IpUtils {

    /** Every form a local call arrives as: a request to localhost is reported as the IPv6 loopback, not as 127.0.0.1. */
    public static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    /** An empty list means localhost only, so an endpoint nobody configured is never reachable from another machine. */
    public static boolean matches(List<String> allowed, String callerIp) {
        if (allowed == null || allowed.isEmpty()) return isLoopback(callerIp);
        return allowed.stream().map(StringUtils::trimLower)
                .anyMatch(entry -> entry.equals("*") || (isLoopback(entry) ? isLoopback(callerIp) : entry.equals(trimLower(callerIp))));
    }
    public static boolean isLoopback(String ip) {
        return LOOPBACK.contains(trimLower(ip));
    }


    /**
     * The address a request really came from. A reverse proxy opens its own connection to the application, so without
     * this every call behind it looks like it came from the proxy - one shared identity for every caller in the world.
     * The forwarded header is believed only when the connection itself came from loopback, because the proxy sits on
     * this machine; trusting it from anywhere else would let any caller claim any address and walk past an allow list.
     */
    public static String callerAddress(String remoteAddr, String forwardedFor) {
        if (!isLoopback(remoteAddr) || StringUtils.isBlank(forwardedFor)) return remoteAddr;
        // the header is a chain written oldest first, so the original caller is the first entry and the rest are proxies
        String first = forwardedFor.split(",")[0].trim();
        return first.isEmpty() ? forwardedFor.trim() : first;
    }

    /**
     * Whether the reverse proxy already checked this caller against a contributed rule for the endpoint it just
     * forwarded - both whether it may call at all and how often - so the application does not check either a
     * second time. Believed only from loopback, for the same reason {@link #callerAddress} is - a caller reaching
     * the application directly could otherwise claim the header itself and walk past its own checks.
     */
    public static boolean alreadyCheckedByProxy(String remoteAddr, String header) {
        return isLoopback(remoteAddr) && "1".equals(header);
    }


    /** Trims the entries, drops the blanks, and collapses a list that already allows everyone down to just "*". */
    public static List<String> normalise(List<String> allowed) {
        if (allowed == null) return List.of();
        List<String> cleaned = allowed.stream().map(StringUtils::trimOrEmpty).filter(entry -> !entry.isEmpty()).distinct().toList();
        return cleaned.contains("*") ? List.of("*") : cleaned;
    }
    /** The first entry that is not an address, so the page can name what it refused instead of failing silently. */
    public static String firstInvalidEntry(List<String> allowed) {
        return allowed == null ? null : allowed.stream().filter(entry -> !isAllowedEntry(entry)).findFirst().orElse(null);
    }
    /** What the API Manager accepts in the box, so a typo is refused at save time rather than locking an endpoint out silently. */
    public static boolean isAllowedEntry(String entry) {
        return trimLower(entry).equals("*") || isLoopback(entry) || isAddress(entry);
    }
    /** Literal addresses only - a hostname is refused because resolving it would need a DNS lookup on every request. */
    public static boolean isAddress(String ip) {
        String value = trimLower(ip);
        if (value.matches("([0-9]{1,3}[.]){3}[0-9]{1,3}")) return Arrays.stream(value.split("[.]")).allMatch(part -> Integer.parseInt(part) <= 255);
        return value.contains(":") && value.matches("[0-9a-f:]+");
    }
}
