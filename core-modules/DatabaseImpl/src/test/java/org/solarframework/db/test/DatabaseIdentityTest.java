package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * Object identity across the read and write paths, running against the same in-memory H2 database
 * as {@link DatabaseCoherencyTest}.
 *
 * <p>Two things are under test and they are separate: an object built with {@code new} must load its
 * associations like a fetched one (the {@code == null} guard on the getters, which a read makes true by
 * dropping the placeholders it hydrated), and a row must map
 * to exactly one Java object whichever path produced it (the identity map fed by every write).
 */
@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:solartest;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseIdentityTest {

    @BeforeAll
    void createSchema() {
        SolarDBManager.createAllSchemasIfMissing();
        SolarDBManager.verifyEntities();
    }

    @BeforeEach
    void cleanDatabase() {
        SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders");
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");
        SolarDBManager.resetAllCaches();
    }

    private User makeNewUserWith2Orders(long id) {
        User u = new User(id, "Loic", "loic@gmail.com");
        u.Upsert();
        new Order(id * 100 + 1, u, "Steak", 1).Upsert();
        new Order(id * 100 + 2, u, "Egg", 2).Upsert();
        return u;
    }

    private User fetchUser(long id) {
        return SolarDBManager.getById(User.class, id).orElseThrow();
    }

    // ====== ASSOCIATIONS ON A HAND-BUILT OBJECT ======

    /** The original complaint: a {@code new} object holds no ORM collection, so its getter has to fetch - and must land on the same children as the fetched object, not a second copy of them. */
    @Test
    void newObjectLoadsTheSameAssociationsAsAFetchedOne() {
        User u = makeNewUserWith2Orders(1);
        assertEquals(List.of("Egg", "Steak"), u.getOrders().stream().map(Order::getItem).sorted().toList(), "a hand-built user must still find its orders");
        List<Order> mine = u.getOrders(), theirs = fetchUser(1).getOrders(); // the lists are each getter's own; the children in them must not be copies
        assertEquals(mine.size(), theirs.size(), "both sides must resolve to the same orders");
        for (int i = 0; i < mine.size(); i++) assertSame(mine.get(i), theirs.get(i), "both sides must resolve to the same orders");
        assertSame(u, u.getOrders().getFirst().getUser(), "and the way back must lead to the user we wrote");
    }

    /**
     * The getter memoizes into the field, so a child written elsewhere only shows up once the row is read
     * again - which now refreshes the object you are holding instead of building you another one.
     */
    @Test
    void reReadingTheRowRefreshesTheAssociationsInPlace() {
        User u = makeNewUserWith2Orders(1);
        assertEquals(2, u.getOrders().size());

        new Order(103L, u, "Milk", 3).Upsert();
        assertEquals(2, u.getOrders().size(), "the list already handed out stays put");

        assertSame(u, fetchUser(1));
        assertEquals(3, u.getOrders().size(), "re-reading the row must drop the stale children and refetch them");

        u.getOrders().stream().filter(o -> o.getID() == 103L).findFirst().orElseThrow().Delete();
        assertEquals(2, fetchUser(1).getOrders().size(), "and the same must hold after a delete");
    }

    // ====== ONE ROW, ONE OBJECT ======

    @Test
    void everyReadPathReturnsTheObjectWeWrote() {
        User u = makeNewUserWith2Orders(1);
        assertSame(u, fetchUser(1), "getById must hand back the object we wrote, not a copy");
        assertSame(u, SolarDBManager.getWhere(User.class, "name = ?", "Loic").orElseThrow());
        assertSame(u, SolarDBManager.getAll(User.class).getFirst());
        assertSame(u, SolarDBManager.getAllWhere(User.class, "email = ?", "loic@gmail.com").getFirst());
    }

    @Test
    void batchWritesAreCanonicalToo() {
        User u1 = new User(1L, "A", "a@example.com");
        User u2 = new User(2L, "B", "b@example.com");
        DatabaseObject.UpsertAll(List.of(u1, u2));

        assertSame(u1, fetchUser(1));
        assertSame(u2, fetchUser(2));
    }

    /** Every write path claims the row, not just the insert ones. ({@code UpsertThenReturn} is left out: H2 has no RETURNING.) */
    @Test
    void updatingClaimsTheRowToo() {
        makeNewUserWith2Orders(1);
        SolarDBManager.resetAllCaches();

        User detached = new User(1L, "Renamed", "renamed@example.com");
        detached.setCreatedAt(Instant.now()); // Update() writes every column, and CreatedAt is NOT NULL
        assertEquals(1, detached.Update());
        assertSame(detached, fetchUser(1));
        assertEquals("Renamed", fetchUser(1).getName());
    }

    /** Reads refresh the canonical object in place - that is what keeps the single instance from going stale. */
    @Test
    void readingRefreshesTheInstanceInPlace() {
        User u = makeNewUserWith2Orders(1);
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "UPDATE user SET name = ? WHERE id = ?", "Renamed", 1L);

        assertSame(u, fetchUser(1));
        assertEquals("Renamed", u.getName(), "the read must copy the row's state onto the object we hold");
    }

    /** Last writer wins: rewriting a row through another object makes that one canonical. */
    @Test
    void rewritingThroughAnotherObjectTakesOverTheRow() {
        makeNewUserWith2Orders(1);
        User other = new User(1L, "Updated", "updated@example.com");
        other.Upsert();

        assertSame(other, fetchUser(1));
        assertEquals(1, SolarDBManager.Count(User.class), "and it is still one row");
    }

    @Test
    void identityIsPerRowNotPerClass() {
        User u1 = makeNewUserWith2Orders(1);
        User u2 = makeNewUserWith2Orders(2);

        assertSame(u1, fetchUser(1));
        assertSame(u2, fetchUser(2));
        assertEquals(2, u1.getOrders().size(), "each user must keep its own orders");
        assertTrue(u1.getOrders().stream().noneMatch(o -> u2.getOrders().contains(o)));
    }

    /** A hard delete must not leave the deleted object behind for the next insert of that id. */
    @Test
    void hardDeleteDropsTheIdentity() {
        User u = new User(1L, "Loic", "loic@gmail.com");
        u.Upsert();
        assertEquals(1, u.TrueDelete());

        User reborn = new User(1L, "Other", "other@example.com");
        reborn.Upsert();
        assertSame(reborn, fetchUser(1));
        assertEquals("Other", fetchUser(1).getName());
    }

    @Test
    void resetAllCachesDropsIdentity() {
        User u = makeNewUserWith2Orders(1);
        SolarDBManager.resetAllCaches();

        User fresh = fetchUser(1);
        assertNotSame(u, fresh, "clearing every cache must start from a blank slate");
        assertEquals("Loic", fresh.getName());
        assertEquals(2, fresh.getOrders().size());
    }

    /** Partial selects map a subset of the columns, so they must stay out of the identity map. */
    @Test
    void partialSelectsNeverOverwriteTheCanonicalObject() {
        User u = makeNewUserWith2Orders(1);
        User partial = SolarDBManager.getById("name", User.class, 1L).orElseThrow();

        assertNotSame(u, partial, "a partial row must not be handed out as the canonical object");
        assertEquals("Loic", u.getName());
        assertEquals("loic@gmail.com", u.getEmail(), "and it must not blank out the columns it never read");
    }
}
