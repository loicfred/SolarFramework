package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

/**
 * Cache-coherency and CRUD combination tests for the Database module,
 * running against an in-memory H2 database so the build needs no external server.
 *
 * Every test follows the same idea: warm the cache through one read path,
 * write through some other path, then assert that every read path sees the change.
 */
@SolarH2Test
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseCoherencyTest {

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

    /** Inserts a user with two orders (IDs id*100+1 and id*100+2). */
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

    private Order fetchOrder(long id) {
        return SolarDBManager.getById(Order.class, id).orElseThrow();
    }

    private List<Order> fetchOrdersOf(long userId) {
        return SolarDBManager.getAllWhere(Order.class, "UserID = ?", userId);
    }

    // ====== BASIC READS ======

    @Test
    void insertAndReadBack() {
        makeNewUserWith2Orders(1);
        User me = fetchUser(1);
        assertEquals("Loic", me.getName());
        assertEquals("loic@gmail.com", me.getEmail());
        assertEquals(2, fetchOrdersOf(1).size());
    }

    @Test
    void repeatedReadsHitTheCache() {
        makeNewUserWith2Orders(1);
        assertSame(fetchUser(1), fetchUser(1), "same query should return the cached instance");
    }

    @Test
    void allReadPathsReturnTheSameData() {
        makeNewUserWith2Orders(1);
        new User(2L, "Bob", "bob@example.com").Upsert();

        User byId = fetchUser(1);
        User byWhere = SolarDBManager.getWhere(User.class, "name = ?", "Loic").orElseThrow();
        List<User> all = SolarDBManager.getAll(User.class);
        List<User> allWhere = SolarDBManager.getAllWhere(User.class, "email = ?", "loic@gmail.com");

        assertEquals(byId.getID(), byWhere.getID());
        assertEquals(2, all.size());
        assertEquals(1, allWhere.size());
        assertEquals(byId.getID(), allWhere.getFirst().getID());
        assertEquals(byId.getName(), allWhere.getFirst().getName());
    }

    // ====== ORDER WRITES MUST REFRESH THE CACHED USER ======

    @Test
    void rawSqlOrderDeleteRefreshesCachedUser() {
        makeNewUserWith2Orders(1);
        assertEquals(2, fetchOrdersOf(1).size(), "user cached with both orders");

        int deleted = SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders WHERE id = ?", 102L);
        assertEquals(1, deleted);

        assertEquals(1, fetchOrdersOf(1).size(), "cached user must be refreshed after raw SQL order delete");
    }

    @Test
    void objectOrderDeleteRefreshesCachedUser() {
        makeNewUserWith2Orders(1);
        User me = fetchUser(1);
        assertEquals(2, fetchOrdersOf(1).size());

        Order first = fetchOrdersOf(1).getFirst();
        assertEquals(1, first.Delete());

        List<Order> remaining = fetchOrdersOf(1);
        assertEquals(1, remaining.size(), "cached user must be refreshed after Order.Delete()");
        assertNotEquals(first.getID(), remaining.getFirst().getID());
    }

    @Test
    void orderUpdateRefreshesCachedUser() {
        makeNewUserWith2Orders(1);
        fetchUser(1); // warm the user cache with its orders

        Order o = fetchOrder(101);
        o.setAmount(99);
        assertEquals(1, o.Update());

        Order fromUser = fetchOrdersOf(1).stream().filter(x -> x.getID() == 101L).findFirst().orElseThrow();
        assertEquals(99, fromUser.getAmount(), "order update must be visible through the user's orders");
    }

    // ====== USER WRITES MUST REFRESH THE CACHED ORDER ======

    @Test
    void userUpdateRefreshesCachedOrder() {
        makeNewUserWith2Orders(1);
        Order o = fetchOrder(101); // warm the order cache
        assertEquals("Loic", o.getUser().getName());

        User me = fetchUser(1);
        me.setName("Renamed");
        assertEquals(1, me.Update());

        assertEquals("Renamed", fetchOrder(101).getUser().getName(), "user update must be visible through the order's user");
    }

    @Test
    void trueDeletingUserWithOrdersFailsAndUserStaysReadable() {
        // Delete() is now a soft delete (sets DeletedAt) so it never touches the FK-constrained
        // column and can't violate it - only TrueDelete() issues a real DELETE that can.
        makeNewUserWith2Orders(1);
        User me = fetchUser(1);

        assertThrows(RuntimeException.class, me::TrueDelete, "FK constraint must block hard-deleting a user that still has orders");

        assertTrue(SolarDBManager.getById(User.class, 1L).isPresent(), "user must still be readable after the failed delete");
        assertEquals(2, fetchOrdersOf(1).size());
    }

    @Test
    void deletingOrdersThenUserRemovesThemFromEveryReadPath() {
        makeNewUserWith2Orders(1);
        new User(2L, "Bob", "bob@example.com").Upsert();
        // warm all caches
        fetchUser(1);
        fetchOrder(101);
        SolarDBManager.getAll(User.class);
        SolarDBManager.getAll(Order.class);

        assertEquals(1, fetchOrder(101).Delete());
        assertEquals(1, SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders WHERE id = ?", 102L));
        assertEquals(1, fetchUser(1).Delete());

        // getById bypasses the soft-delete filter on purpose - a caller who already has the ID can still fetch the row
        assertTrue(SolarDBManager.getById(User.class, 1L).map(User::getDeletedAt).isPresent(), "getById must still find a soft-deleted user");
        assertTrue(SolarDBManager.getById(Order.class, 101L).map(Order::getDeletedAt).isPresent(), "getById must still find a soft-deleted order");
        assertTrue(SolarDBManager.getAll(Order.class).isEmpty(), "listing must exclude the soft-deleted order");
        List<User> all = SolarDBManager.getAll(User.class);
        assertEquals(1, all.size());
        assertEquals(2L, all.getFirst().getID());
    }

    // ====== INSERTS MUST INVALIDATE CACHED EMPTY / LIST / COUNT RESULTS ======

    @Test
    void insertIsVisibleAfterCachedEmptyRead() {
        assertTrue(SolarDBManager.getById(User.class, 7L).isEmpty(), "caches the empty result");

        new User(7L, "New", "new@example.com").Upsert();

        assertEquals("New", fetchUser(7).getName(), "insert must invalidate the cached empty result");
    }

    @Test
    void getAllAndCountReflectEveryWrite() {
        makeNewUserWith2Orders(1);
        assertEquals(1, SolarDBManager.getAll(User.class).size());
        assertEquals(1, SolarDBManager.Count(User.class));
        assertEquals(2, SolarDBManager.Count(Order.class));

        new User(2L, "Bob", "bob@example.com").Upsert();
        assertEquals(2, SolarDBManager.getAll(User.class).size(), "getAll must see the new user");
        assertEquals(2, SolarDBManager.Count(User.class), "Count must see the new user");

        new Order(103L, fetchUser(1), "Milk", 3).Upsert();
        assertEquals(3, SolarDBManager.Count(Order.class));

        fetchOrder(103).Delete();
        assertEquals(2, SolarDBManager.Count(Order.class), "Count must see the deleted order");
    }

    @Test
    void getWhereReflectsUpdate() {
        makeNewUserWith2Orders(1);
        assertTrue(SolarDBManager.getWhere(User.class, "name = ?", "Ghost").isEmpty(), "caches the empty result");

        User me = fetchUser(1);
        me.setName("Ghost");
        assertEquals(1, me.Update());

        assertTrue(SolarDBManager.getWhere(User.class, "name = ?", "Ghost").isPresent());
        assertTrue(SolarDBManager.getWhere(User.class, "name = ?", "Loic").isEmpty());
        assertEquals(1, SolarDBManager.getAllWhere(User.class, "name = ?", "Ghost").size());
    }

    // ====== WRITE VARIANTS ======

    @Test
    void upsertUpdatesTheExistingRow() {
        makeNewUserWith2Orders(1);
        fetchUser(1); // warm cache

        new User(1L, "Updated", "updated@example.com").Upsert();

        assertEquals(1, SolarDBManager.Count(User.class), "upsert must not duplicate the row");
        User fresh = fetchUser(1);
        assertEquals("Updated", fresh.getName());
        assertEquals("updated@example.com", fresh.getEmail());
    }

    @Test
    void updateOnlyPersistsJustThatColumn() {
        makeNewUserWith2Orders(1);
        User me = fetchUser(1);
        me.setName("OnlyName");
        me.setEmail("changed@example.com");

        assertEquals(1, me.UpdateOnly("name"));

        User fresh = fetchUser(1);
        assertEquals("OnlyName", fresh.getName());
        assertEquals("loic@gmail.com", fresh.getEmail(), "email was not part of UpdateOnly and must keep its DB value");
    }

    @Test
    void incrementColumnIsVisibleThroughEveryReadPath() {
        makeNewUserWith2Orders(1);
        fetchUser(1); // warm cache
        Order o = fetchOrder(101);
        assertEquals(1, o.getAmount());

        o.IncrementColumn("amount", 5);

        assertEquals(6, o.getAmount(), "in-memory object must be incremented");
        assertEquals(6, fetchOrder(101).getAmount(), "fresh read must see the increment");
        assertEquals(6, fetchOrdersOf(1).stream().filter(x -> x.getID() == 101L).findFirst().orElseThrow().getAmount(),
                "the user's cached orders must see the increment");
    }

    @Test
    void upsertBatchIsVisibleInReads() {
        assertTrue(SolarDBManager.getAll(User.class).isEmpty(), "caches the empty list");

        User u1 = new User(1L, "A", "a@example.com");
        User u2 = new User(2L, "B", "b@example.com");
        Instant now = Instant.now();
        for (User u : List.of(u1, u2)) {
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
        }
        DatabaseService service = (DatabaseService) SolarDBManager.getDefaultService();
        service.UpsertBatch(List.of(u1, u2));

        assertEquals(2, SolarDBManager.getAll(User.class).size(), "batch upsert must invalidate the cached list");
        assertEquals(2, SolarDBManager.Count(User.class));
        assertEquals("A", fetchUser(1).getName());
    }

    /** The point of an *upsert* batch: a second pass over rows that already exist has to update them, not fail or duplicate. */
    @Test
    void upsertBatchUpdatesRowsThatAlreadyExist() {
        User u1 = new User(1L, "A", "a@example.com");
        User u2 = new User(2L, "B", "b@example.com");
        DatabaseObject.UpsertAll(List.of(u1, u2));

        u1.setName("A2");
        u2.setName("B2");
        DatabaseObject.UpsertAll(List.of(u1, u2));

        assertEquals(2, SolarDBManager.Count(User.class), "an upsert of existing rows must not insert duplicates");
        assertEquals("A2", fetchUser(1).getName());
        assertEquals("B2", fetchUser(2).getName());
    }

    /**
     * A batch carries one column list for every row, so unlike a single write it cannot leave a null column out -
     * it has to bind the null. Hibernate infers a parameter's JDBC type from the value it is given, and a bare
     * null tells it nothing, so this used to die on "No JDBC mapping could be inferred for parameter".
     */
    @Test
    void upsertBatchWritesRowsWithNullColumns() {
        User named = new User(1L, "A", "a@example.com");
        User anonymous = new User(2L, "B", null);
        DatabaseObject.UpsertAll(List.of(named, anonymous));

        assertEquals(2, SolarDBManager.Count(User.class));
        assertNull(fetchUser(2).getEmail(), "the null must be stored as a null, not skipped or defaulted");
        assertEquals("a@example.com", fetchUser(1).getEmail(), "the row that did have a value keeps it");
    }

    /**
     * A @Transient field is the object's own business, whatever it happens to be called. User.tags is named after
     * the real "Tags" column on purpose: fields are matched to columns by field name, so before this was filtered
     * the transient List was handed to the native query as a parameter and the write died on
     * "No JDBC mapping could be inferred for parameter".
     */
    @Test
    void transientFieldsAreNeverWrittenEvenWhenNamedAfterAColumn() {
        User u = new User(1L, "A", "a@example.com");
        u.setTagsText("kept");
        assertDoesNotThrow(() -> DatabaseObject.UpsertAll(List.of(u)), "a transient field must not reach the query");
        assertEquals(1, SolarDBManager.Count(User.class));

        // Since 31 Aug 2026 the whole path names columns through DatabaseObject.columnOf, so the String that
        // owns "Tags" is written and read back under that name however it is called in Java, while the transient
        // List named after the same column stays the object's own business.
        assertEquals("kept", fetchUser(1).getTagsText(), "a field whose column is named differently must still be written");
    }

    /** The renamed column has to travel the update paths as well as the insert, and UpdateOnly is asked for it by the column's own name. */
    @Test
    void aRenamedColumnIsWrittenByUpdateAndByUpdateOnly() {
        User u = new User(1L, "A", "a@example.com");
        u.setTagsText("first");
        u.Upsert();

        User stored = fetchUser(1);
        stored.setTagsText("second");
        stored.Update();
        assertEquals("second", fetchUser(1).getTagsText(), "Update has to name the column, not the field");

        stored.setTagsText("third");
        stored.UpdateOnly("Tags");
        assertEquals("third", fetchUser(1).getTagsText(), "UpdateOnly matches the column the caller named");
    }

    /** UpsertAll fires the lifecycle hook per item, so the timestamps a RECORD_OBJ needs are filled in by the batch too. */
    @Test
    void upsertAllStampsRecordTimestamps() {
        User u = new User(1L, "A", "a@example.com");
        assertNull(u.getCreatedAt(), "sanity: nothing has stamped it yet");
        DatabaseObject.UpsertAll(List.of(u));
        assertNotNull(fetchUser(1).getCreatedAt(), "CreatedAt must be written by the batch path");
        assertNotNull(fetchUser(1).getUpdatedAt(), "UpdatedAt must be written by the batch path");
    }

    @Test
    void insertBatchWritesEveryRow() {
        DatabaseService service = (DatabaseService) SolarDBManager.getDefaultService();
        Instant now = Instant.now();
        List<User> users = List.of(new User(1L, "A", "a@example.com"), new User(2L, "B", "b@example.com"), new User(3L, "C", "c@example.com"));
        for (User u : users) { u.setCreatedAt(now); u.setUpdatedAt(now); }
        service.InsertBatch(users);

        assertEquals(3, SolarDBManager.Count(User.class), "every row of the batch must land, not just the first");
        assertEquals("C", fetchUser(3).getName());
    }

    @Test
    void batchOfNothingIsANoOp() {
        assertEquals(0, DatabaseObject.UpsertAll(List.of()));
        assertEquals(0, ((DatabaseService) SolarDBManager.getDefaultService()).InsertBatch(List.of()));
        assertEquals(0, SolarDBManager.Count(User.class));
    }
}
