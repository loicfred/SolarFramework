package org.solarframework.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpUtilsTest {

    @Test
    void emptyListAllowsLocalhostOnly() {
        assertTrue(IpUtils.matches(List.of(), "127.0.0.1"));
        assertTrue(IpUtils.matches(null, "0:0:0:0:0:0:0:1"));
        assertFalse(IpUtils.matches(List.of(), "192.168.1.50"));
    }
    @Test
    void starAllowsAnyone() {
        assertTrue(IpUtils.matches(List.of("*"), "8.8.8.8"));
        assertTrue(IpUtils.matches(List.of("127.0.0.1", "*"), "8.8.8.8"));
    }
    @Test
    void listAllowsOnlyTheAddressesInIt() {
        assertTrue(IpUtils.matches(List.of("192.168.1.50", "10.0.0.4"), "10.0.0.4"));
        assertFalse(IpUtils.matches(List.of("192.168.1.50"), "192.168.1.51"));
    }
    @Test
    void entriesAreTrimmed() {
        assertTrue(IpUtils.matches(List.of("  192.168.1.50  "), "192.168.1.50"));
    }
    @Test
    void anyLocalhostEntryMatchesAnyLoopbackForm() {
        assertTrue(IpUtils.matches(List.of("localhost"), "0:0:0:0:0:0:0:1"));
        assertTrue(IpUtils.matches(List.of("127.0.0.1"), "::1"));
        assertTrue(IpUtils.matches(List.of("::1"), "127.0.0.1"));
    }
    @Test
    void nullCallerIsRefusedUnlessEveryoneIsAllowed() {
        assertFalse(IpUtils.matches(List.of("127.0.0.1"), null));
        assertTrue(IpUtils.matches(List.of("*"), null));
    }

    @Test
    void isLoopbackKnowsEveryLocalForm() {
        assertTrue(IpUtils.isLoopback("localhost"));
        assertTrue(IpUtils.isLoopback("::1"));
        assertFalse(IpUtils.isLoopback("192.168.1.50"));
    }

    @Test
    void normaliseTrimsDropsBlanksAndDeduplicates() {
        assertEquals(List.of("127.0.0.1", "10.0.0.4"), IpUtils.normalise(List.of("  127.0.0.1 ", "", "10.0.0.4", "127.0.0.1")));
        assertEquals(List.of(), IpUtils.normalise(null));
    }
    @Test
    void normaliseCollapsesAListThatAlreadyAllowsEveryone() {
        assertEquals(List.of("*"), IpUtils.normalise(List.of("127.0.0.1", "*", "10.0.0.4")));
    }

    @Test
    void firstInvalidEntryNamesTheOffendingEntry() {
        assertEquals("example.com", IpUtils.firstInvalidEntry(List.of("127.0.0.1", "example.com", "10.0.0.4")));
        assertNull(IpUtils.firstInvalidEntry(List.of("*", "localhost", "10.0.0.4")));
        assertNull(IpUtils.firstInvalidEntry(null));
    }

    @Test
    void isAllowedEntryAcceptsStarLocalhostAndLiterals() {
        assertTrue(IpUtils.isAllowedEntry("*"));
        assertTrue(IpUtils.isAllowedEntry("localhost"));
        assertTrue(IpUtils.isAllowedEntry("192.168.1.50"));
        assertTrue(IpUtils.isAllowedEntry("2001:db8::1"));
    }
    @Test
    void isAllowedEntryRefusesHostnamesAndNonsense() {
        assertFalse(IpUtils.isAllowedEntry("example.com"));
        assertFalse(IpUtils.isAllowedEntry("192.168.1"));
        assertFalse(IpUtils.isAllowedEntry("999.1.1.1"));
        assertFalse(IpUtils.isAllowedEntry(""));
    }


    @Test
    void aForwardedAddressIsBelievedOnlyFromTheProxyOnThisMachine() {
        assertEquals("203.0.113.9", IpUtils.callerAddress("127.0.0.1", "203.0.113.9"));
        assertEquals("203.0.113.9", IpUtils.callerAddress("::1", "203.0.113.9"));
    }
    @Test
    void aForwardedAddressFromAnywhereElseIsIgnored() {
        assertEquals("198.51.100.7", IpUtils.callerAddress("198.51.100.7", "203.0.113.9"), "a caller must not be able to claim another address");
    }
    @Test
    void theFirstEntryOfTheChainIsTheOriginalCaller() {
        assertEquals("203.0.113.9", IpUtils.callerAddress("127.0.0.1", "203.0.113.9, 10.0.0.1, 10.0.0.2"));
    }
    @Test
    void withoutAForwardedHeaderTheSocketAddressStands() {
        assertEquals("127.0.0.1", IpUtils.callerAddress("127.0.0.1", null));
        assertEquals("127.0.0.1", IpUtils.callerAddress("127.0.0.1", "   "));
    }


    @Test
    void theProxyGuardHeaderIsBelievedOnlyFromTheProxyOnThisMachine() {
        assertTrue(IpUtils.alreadyCheckedByProxy("127.0.0.1", "1"));
        assertTrue(IpUtils.alreadyCheckedByProxy("::1", "1"));
    }
    @Test
    void theProxyGuardHeaderFromAnywhereElseIsIgnored() {
        assertFalse(IpUtils.alreadyCheckedByProxy("198.51.100.7", "1"), "a direct caller must not be able to claim the proxy already checked it");
    }
    @Test
    void anythingOtherThanExactlyOneRefusesTheClaim() {
        assertFalse(IpUtils.alreadyCheckedByProxy("127.0.0.1", "0"));
        assertFalse(IpUtils.alreadyCheckedByProxy("127.0.0.1", null));
        assertFalse(IpUtils.alreadyCheckedByProxy("127.0.0.1", "true"));
    }
}
