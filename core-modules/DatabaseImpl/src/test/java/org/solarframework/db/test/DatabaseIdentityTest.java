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
 * <p>Two things are under test: an association reports what its object holds and nothing else, and a row
 * maps to exactly one Java object whichever path produced it - which is what makes a child queried
 * separately the same object as the one attached in memory.
 */
@SolarH2Test
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

    private List<Order> fetchOrdersOf(long userId) {
        return SolarDBManager.getAllWhere(Order.class, "UserID = ?", userId);
    }

    // ====== ASSOCIATIONS ======


    @Test
    void aReadHandsBackItsChildren() {
        makeNewUserWith2Orders(1);
        assertEquals(List.of("Egg", "Steak"), fetchUser(1).getOrders().stream().map(Order::getItem).sorted().toList(), "a fetched user must report the orders it owns");
    }

    /**
     * The static/no-transaction case this was written for: read the owner first (canonical via EntityIdentity),
     * then walk its lazily-faulted orders - each child's back-reference must already be the exact owner
     * instance, wired by DBInstanceService#replaceInverseCollections at PostLoad time, not resolved via a
     * second query the way an untouched Hibernate association proxy would need.
     */
    @Test
    void collectionChildrenAlreadyHaveTheirOwnerWiredWithNoExtraQuery() {
        User u = makeNewUserWith2Orders(1);
        SolarDBManager.resetAllCaches();
        User reread = fetchUser(1);

        assertSame(reread, reread.getOrders().getFirst().getUser());
        assertSame(reread, reread.getOrders().getLast().getUser());
    }

    /**
     * The field is no longer Hibernate's own PersistentBag - PostLoad replaces it with a lazy list backed by
     * the framework's own query path (see DBInstanceService#replaceInverseCollections), specifically so a
     * loaded child's back-reference can be wired to the owner with no per-getter code and no extra query
     * once the owner is already known. It must still stay unfaulted until something actually touches it.
     */
    @Test
    void aReadLeavesAFaultableListRatherThanAnEmptyList() {
        makeNewUserWith2Orders(1);
        SolarDBManager.resetAllCaches(); // so the read hands back a fresh lazy list, not the registered writer's
        User u = fetchUser(1);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(u.orders.getClass()), "the read must leave a lazy, faultable list in place - nulling it is what made every child invisible");
        assertEquals(2, u.getOrders().size(), "first access faults it, closed EntityManager or not");
    }

    /**
     * The replacement must satisfy the field's own declared type, the way Hibernate picks PersistentSet or
     * PersistentBag off it - a Set-declared inverse collection used to die at Field#set with "Can not set
     * java.util.Set field ... to jdk.proxy1.$ProxyN" because every field got a List proxy.
     */
    @Test
    void aSetDeclaredInverseCollectionFaultsTheSameWayAListDoes() {
        makeNewUserWith2Orders(1);
        SolarDBManager.resetAllCaches();
        User u = fetchUser(1);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(u.orderSet.getClass()), "a Set field must be left lazy too, not loaded eagerly");
        assertEquals(List.of("Egg", "Steak"), u.getOrderSet().stream().map(Order::getItem).sorted().toList(), "and it must fault into the same children the List sees");
    }

    @Test
    void aHandBuiltObjectStillStartsWithAnEmptyMutableList() {
        User u = new User(2L, "Fred", "fred@gmail.com");
        assertTrue(u.getOrders().isEmpty(), "a new object owns no children yet");
        u.addOrder("Milk", 3);
        assertEquals(1, u.getOrders().size(), "and its list must be mutable, so a graph can be built before it is saved");
    }

    @Test
    void attachedChildrenAreTheSameObjectsTheDatabaseHandsBack() {
        User u = makeNewUserWith2Orders(1);
        assertTrue(u.getOrders().isEmpty(), "the writer never held its children either - it wrote them and let go");

        Order milk = u.addOrder("Milk", 3);
        assertEquals(List.of(milk), u.getOrders(), "addOrder must attach to the object that owns it");
        milk.Upsert();

        for (Order o : fetchOrdersOf(1)) if (o.getID().equals(milk.getID())) assertSame(milk, o, "a child read back must be the object we attached");
        assertSame(u, fetchOrdersOf(1).getFirst().getUser(), "and the way back must lead to the user we wrote");
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
        assertEquals(2, fetchOrdersOf(1).size(), "each user must keep its own orders");
        assertTrue(fetchOrdersOf(1).stream().noneMatch(o -> fetchOrdersOf(2).contains(o)));
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
        assertEquals(2, fetchOrdersOf(1).size());
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
