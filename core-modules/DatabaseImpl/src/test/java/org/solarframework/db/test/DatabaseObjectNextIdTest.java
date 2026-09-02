package org.solarframework.db.test;

import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseObject;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Identifiers are handed out in loops far faster than the clock they read, so the only interesting question is whether two rows can ever claim the same one. */
class DatabaseObjectNextIdTest {

    @Test
    void aThousandIdentifiersTakenInARowAreAllDifferent() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) assertTrue(seen.add(DatabaseObject.nextId()), "identifier handed out twice");
    }
    @Test
    void identifiersOnlyEverGoForward() {
        long[] ids = IntStream.range(0, 500).mapToLong(i -> DatabaseObject.nextId()).toArray();
        for (int i = 1; i < ids.length; i++) assertTrue(ids[i] > ids[i - 1]);
    }
    /** An identifier never reads as the past. It may read slightly ahead of the clock, because a burst longer than a millisecond has to keep stepping forward to stay unique - that drift is the cost of the guarantee above. */
    @Test
    void anIdentifierNeverReadsAsThePast() {
        long before = Instant.now().toEpochMilli();
        assertTrue(DatabaseObject.nextId() >= before);
    }
    @Test
    void concurrentCallersNeverCollide() throws InterruptedException {
        Set<Long> seen = java.util.Collections.synchronizedSet(new HashSet<>());
        Set<Long> duplicates = java.util.Collections.synchronizedSet(new HashSet<>());
        Thread[] threads = IntStream.range(0, 8).mapToObj(t -> new Thread(() -> {
            for (int i = 0; i < 500; i++) {
                long id = DatabaseObject.nextId();
                if (!seen.add(id)) duplicates.add(id);
            }
        })).toArray(Thread[]::new);
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();
        assertTrue(duplicates.isEmpty(), "identifiers handed out twice: " + duplicates);
    }
}
