# JPA Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make SolarFramework's entities participate in real Spring `@Transactional`/Hibernate dirty-checking when a transaction is active, while leaving today's static (no-transaction) usage byte-for-byte unchanged.

**Architecture:** Each registered `DatabaseService` gains a real, programmatically-built `LocalContainerEntityManagerFactoryBean` + `JpaTransactionManager`. Every read/write entry point checks whether a transaction is already bound, on the current thread, to that specific source — if bound, it uses a shared transaction-scoped `EntityManager` (real managed entities, real dirty-checking); if not, it falls through to the existing native-SQL-per-call code, completely untouched.

**Tech Stack:** Java 25, Spring Boot 4.1.0 (Spring ORM, Spring TX), Hibernate ORM 7.4.1 (`LocalContainerEntityManagerFactoryBean`, `JpaTransactionManager`, `SharedEntityManagerCreator`, `TransactionSynchronizationManager`), H2 (tests, matching the existing `DatabaseCoherencyTest`/`DatabaseIdentityTest` setup), JUnit 5 + Spring Boot Test.

## Global Constraints

- Public API must not change: `SolarDBManager.getById/getWhere/getAll/getAllWhere/Count`, and `DatabaseObject`'s `.Upsert()/.Update()/.UpdateOnly()/.Delete()/.IncrementColumn()`, keep their exact signatures.
- No ambient transaction ("static" usage) must behave identically to today's code — this is enforced by never modifying `DatabaseUtils.withEm`/`inTransaction`, `EntityIdentity`, or `DBInstanceService`'s existing native-SQL branch; new code is additive, reached only when a transaction is bound.
- No cross-datasource atomicity (JTA/XA) — explicitly out of scope (see spec's Non-goals).
- N+1/batch-fetch handling — explicitly out of scope (see spec's Deferred follow-ups).
- `UpdateOnly(String... columns)` and `IncrementColumn`/`IncrementColumns` partial-column semantics have no clean 1:1 JPA translation (dirty-checking flushes *all* changed columns, not a caller-chosen subset) — deferred; Task 6 covers full-row `Update()`/`Upsert()`/`Delete()` only, and documents this gap explicitly rather than silently leaving it half-done.
- Source: `docs/superpowers/specs/2026-08-08-jpa-compatibility-design.md`. Every task below implements a named section of that spec.

---

## File Structure

All new/modified files live in `core-modules/DatabaseImpl` (package `org.solarframework.db.spring`) and its test tree. **`DatabaseAPI` needs zero changes** — `DatabaseObject.Update()`/`Upsert()`/etc. already just delegate to `getService()` (an `IDBObjectService<T>`, implemented by `DBInstanceService` in `DatabaseImpl`), so the resolver branch belongs entirely inside `DBInstanceService` and `DatabaseService`, not the API layer.

- **Create** `JpaSourceRegistrar.java` — builds/tears down the per-source `EntityManagerFactory` + `JpaTransactionManager`.
- **Create** `TransactionResolver.java` — the one "is a transaction bound to this source, on this thread?" check.
- **Create** `TransactionalAccess.java` — JPA-native `getById`/`getWhere`/`getAll`/`getAllWhere`/`Count`/write-flush against the shared `EntityManager`.
- **Create** `InverseAssociationWiringListener.java` — generic `@OneToMany(mappedBy=...)` back-reference auto-wiring.
- **Modify** `DatabaseService.java` — hold the `JpaSourceRegistrar.JpaSourceBeans` for this source; branch `getById`/`getWhere`/`getAll`/`getAllWhere`/`Count` through the resolver.
- **Modify** `DatabaseManager.java` — inject `ConfigurableApplicationContext`; call `JpaSourceRegistrar.register`/`unregister` from `addSource`/`removeNonDefaultSources`; register `InverseAssociationWiringListener` alongside the existing `PostLoadEventListener` registration.
- **Modify** `DBInstanceService.java` — branch `Update`/`Upsert`/`Delete` through the resolver.

---

### Task 1: Per-source EntityManagerFactory + JpaTransactionManager bootstrap

**Files:**
- Create: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/JpaSourceRegistrar.java`
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseService.java`
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/JpaSourceRegistrarTest.java`

**Interfaces:**
- Produces: `JpaSourceRegistrar.JpaSourceBeans(EntityManagerFactory entityManagerFactory, JpaTransactionManager transactionManager)`, `JpaSourceRegistrar.register(DatabaseService, ConfigurableApplicationContext) -> JpaSourceBeans`, `JpaSourceRegistrar.beanNameOf(DatabaseService) -> String`. `DatabaseService.getJpaBeans() -> JpaSourceRegistrar.JpaSourceBeans` (nullable until `register` has run for that source).

- [ ] **Step 1: Write the failing test**

Create `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/JpaSourceRegistrarTest.java`:

```java
package org.solarframework.db.test;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:jpacompat;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class JpaSourceRegistrarTest {

    @Autowired
    ConfigurableApplicationContext context;

    @Test
    void registersAFunctionalEntityManagerFactory() {
        SolarDBManager.createAllSchemasIfMissing();
        DatabaseService source = (DatabaseService) SolarDBManager.getDefaultService();

        JpaSourceRegistrar.JpaSourceBeans beans = JpaSourceRegistrar.register(source, context);

        assertNotNull(beans.entityManagerFactory());
        assertTrue(beans.entityManagerFactory().isOpen());
        assertSame(beans, source.getJpaBeans(), "DatabaseService must hold onto the beans it was just given");

        assertTrue(context.getBeanFactory().containsSingleton(JpaSourceRegistrar.beanNameOf(source) + "_emf"));
        assertTrue(context.getBeanFactory().containsSingleton(JpaSourceRegistrar.beanNameOf(source) + "_txManager"));

        try (EntityManager em = beans.entityManagerFactory().createEntityManager()) {
            em.getTransaction().begin();
            User u = new User(9001L, "Registrar", "registrar@example.com");
            u.setCreatedAt(java.time.Instant.now());
            u.setUpdatedAt(java.time.Instant.now());
            em.persist(u);
            em.getTransaction().commit();
        }

        assertTrue(SolarDBManager.getById(User.class, 9001L).isPresent(), "a row written through the new EMF must be visible through the existing native-SQL read path too - same table, same connection");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=JpaSourceRegistrarTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: compile failure — `JpaSourceRegistrar` and `DatabaseService.getJpaBeans()` don't exist yet.

- [ ] **Step 3: Write `JpaSourceRegistrar`**

```java
package org.solarframework.db.spring;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.MutablePersistenceUnitInfo;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a real, Spring-managed EntityManagerFactory + JpaTransactionManager for one DatabaseService, so
 * @Transactional - this framework's own, or a host application's - can bind a genuine persistence context to
 * it. Built programmatically rather than through Spring Boot's startup autoconfiguration because sources can
 * be registered at runtime (see DatabaseManager#addSource). Entity classes are listed explicitly rather than
 * discovered by classpath scanning, matching how DatabaseManager already resolves them per custom classloader.
 */
public final class JpaSourceRegistrar {
    private JpaSourceRegistrar() {}

    public record JpaSourceBeans(EntityManagerFactory entityManagerFactory, JpaTransactionManager transactionManager) {}

    public static JpaSourceBeans register(DatabaseService source, ConfigurableApplicationContext context) {
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource(source.getDataSource());
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emfBean.setPersistenceUnitName(beanNameOf(source));
        emfBean.setPersistenceUnitPostProcessors(pui -> {
            if (pui instanceof MutablePersistenceUnitInfo mpui) {
                for (Class<?> c : source.getEntitiesClasses()) mpui.addManagedClassName(c.getName());
                mpui.setExcludeUnlistedClasses(true);
            }
        });
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", source.getDatabaseType().getDialectClass());
        emfBean.setJpaPropertyMap(props);
        emfBean.afterPropertiesSet();

        EntityManagerFactory emf = emfBean.getObject();
        JpaTransactionManager txManager = new JpaTransactionManager(emf);

        context.getBeanFactory().registerSingleton(beanNameOf(source) + "_emf", emf);
        context.getBeanFactory().registerSingleton(beanNameOf(source) + "_txManager", txManager);

        JpaSourceBeans beans = new JpaSourceBeans(emf, txManager);
        source.setJpaBeans(beans);
        return beans;
    }

    static String beanNameOf(DatabaseService source) {
        return "solarJpa_" + source.getName().replaceAll("\\W+", "_");
    }
}
```

- [ ] **Step 4: Add the field/getter/setter to `DatabaseService`**

In `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseService.java`, add near the other `private transient` fields (next to `sessionFactory`):

```java
@JsonIgnore
private transient JpaSourceRegistrar.JpaSourceBeans jpaBeans;

public JpaSourceRegistrar.JpaSourceBeans getJpaBeans() {
    return jpaBeans;
}
public void setJpaBeans(JpaSourceRegistrar.JpaSourceBeans jpaBeans) {
    this.jpaBeans = jpaBeans;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=JpaSourceRegistrarTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS. If `emfBean.afterPropertiesSet()` throws, the most likely cause is the dialect string or the `MutablePersistenceUnitInfo` cast failing silently (some Spring versions wrap the `PersistenceUnitInfo` differently) - read the actual exception message before changing anything else.

- [ ] **Step 6: Run the full existing DatabaseImpl suite to confirm zero regression**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am`
Expected: still 46/46 passing, unchanged - this task only adds code, nothing yet calls it from an existing path.

- [ ] **Step 7: Commit**

```bash
git add core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/JpaSourceRegistrar.java core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseService.java core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/JpaSourceRegistrarTest.java
git commit -m "Add per-source EntityManagerFactory/JpaTransactionManager bootstrap"
```

---

### Task 2: Teardown on source removal

**Files:**
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/JpaSourceRegistrar.java`
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseManager.java`
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/JpaSourceRegistrarTest.java`

**Interfaces:**
- Consumes: `JpaSourceRegistrar.beanNameOf` (Task 1).
- Produces: `JpaSourceRegistrar.unregister(DatabaseService, ConfigurableApplicationContext)`.

- [ ] **Step 1: Write the failing test**

Add to `JpaSourceRegistrarTest.java`:

```java
    @Test
    void unregisterClosesTheFactoryAndRemovesTheBeans() {
        DatabaseService source = (DatabaseService) SolarDBManager.getDefaultService();
        JpaSourceRegistrar.JpaSourceBeans beans = JpaSourceRegistrar.register(source, context);
        String emfName = JpaSourceRegistrar.beanNameOf(source) + "_emf";
        String txName = JpaSourceRegistrar.beanNameOf(source) + "_txManager";

        JpaSourceRegistrar.unregister(source, context);

        assertFalse(beans.entityManagerFactory().isOpen(), "unregister must close the factory, not just drop the bean reference");
        assertFalse(context.getBeanFactory().containsSingleton(emfName));
        assertFalse(context.getBeanFactory().containsSingleton(txName));
        assertNull(source.getJpaBeans());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=JpaSourceRegistrarTest#unregisterClosesTheFactoryAndRemovesTheBeans" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: compile failure - `unregister` doesn't exist yet.

- [ ] **Step 3: Add `unregister` to `JpaSourceRegistrar`**

```java
    public static void unregister(DatabaseService source, ConfigurableApplicationContext context) {
        JpaSourceBeans beans = source.getJpaBeans();
        if (beans == null) return;
        context.getBeanFactory().destroySingleton(beanNameOf(source) + "_emf");
        context.getBeanFactory().destroySingleton(beanNameOf(source) + "_txManager");
        beans.entityManagerFactory().close();
        source.setJpaBeans(null);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=JpaSourceRegistrarTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS (both tests).

- [ ] **Step 5: Wire registration into `DatabaseManager.addSource`/`removeNonDefaultSources`**

In `DatabaseManager.java`, inject the application context and call the registrar. Change the constructor and add the calls:

```java
    @JsonIgnore
    private transient final ConfigurableApplicationContext context;

    protected DatabaseManager(@Qualifier("databaseCacheManager") CacheManager dbCacheManager, DatabaseService defaultService, ConfigurableApplicationContext context) {
        defaultConnectionString = defaultService.getConnectionString();
        this.dbCacheManager = dbCacheManager;
        this.context = context;
        defaultService.setManager(this);
        addSource(defaultService);
        DefaultDBService = defaultService;
        SolarDBManager = this;
        reload();
    }
```

In `addSource(IDatabaseService ds)`, register the JPA beans right after the source is accepted:

```java
    public boolean addSource(IDatabaseService ds) {
        if (ds.getConnectionString().isEmpty() || ds.getName().isEmpty() || ds.getPassword().isEmpty() || ds.getUsername().isEmpty() || getSources().stream().anyMatch(d -> d.getConnectionString().equalsIgnoreCase(ds.getConnectionString()) || d.getName().equals(ds.getName()))) return false;
        if (getDefaultService() != null && ds.isDefault()) return false;
        storedDataSources.add(ds);
        if (ds instanceof DatabaseService concrete) JpaSourceRegistrar.register(concrete, context);
        return true;
    }
```

In `removeNonDefaultSources()`, unregister before dropping:

```java
    public boolean removeNonDefaultSources() {
        return getSources().removeIf(ds -> {
            if (!ds.isDefault()) {
                ds.reload();
                if (ds instanceof DatabaseService concrete) JpaSourceRegistrar.unregister(concrete, context);
            }
            return !ds.isDefault();
        });
    }
```

- [ ] **Step 6: Run the full existing DatabaseImpl suite to confirm zero regression**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am`
Expected: 46/46 still passing, plus the 2 new `JpaSourceRegistrarTest` tests. If the default source's construction path (`DatabaseManager`'s own constructor calling `addSource(defaultService)`) now fails because the `ConfigurableApplicationContext` isn't fully initialized yet at that point in Spring's bean-creation order, change the default source's registration to happen in an `@EventListener(ApplicationReadyEvent.class)` or `@PostConstruct` method instead of inline in the constructor - note the actual failure before picking which fix, they have different tradeoffs (a `@PostConstruct` on `DatabaseManager` runs after its own construction but still during context refresh, which may be early enough).

- [ ] **Step 7: Commit**

```bash
git add core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/JpaSourceRegistrar.java core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseManager.java core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/JpaSourceRegistrarTest.java
git commit -m "Register/deregister per-source JPA beans on source add/remove"
```

---

### Task 3: Transaction resolver

**Files:**
- Create: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/TransactionResolver.java`
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionResolverTest.java`

**Interfaces:**
- Consumes: `DatabaseService.getJpaBeans()` (Task 1).
- Produces: `TransactionResolver.isBound(DatabaseService) -> boolean`.

- [ ] **Step 1: Write the failing test**

Create `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionResolverTest.java`:

```java
package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.spring.TransactionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:txresolver;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class TransactionResolverTest {

    @Autowired
    ConfigurableApplicationContext context;
    DatabaseService source;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
    }

    @Test
    void falseOutsideAnyTransaction() {
        assertFalse(TransactionResolver.isBound(source));
    }

    @Test
    void trueInsideATransactionBoundToThisSource() {
        TransactionTemplate tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        Boolean bound = tx.execute(status -> TransactionResolver.isBound(source));
        assertTrue(bound);
    }

    @Test
    void falseAgainAfterTheTransactionCompletes() {
        TransactionTemplate tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        tx.execute(status -> null);
        assertFalse(TransactionResolver.isBound(source), "the resource must be unbound once the transaction finishes, or every later static call would wrongly think one is active");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=TransactionResolverTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: compile failure - `TransactionResolver` doesn't exist.

- [ ] **Step 3: Write `TransactionResolver`**

```java
package org.solarframework.db.spring;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one branch point every read/write entry point checks: is a transaction already bound, on this thread,
 * to this specific source's EntityManagerFactory? JpaTransactionManager binds its EntityManagerHolder keyed
 * by the EntityManagerFactory instance itself, so checking hasResource(emf) answers "is THIS source's
 * transaction active right now" - not "is any transaction active anywhere", which matters once more than one
 * source is registered.
 */
public final class TransactionResolver {
    private TransactionResolver() {}

    public static boolean isBound(DatabaseService source) {
        JpaSourceRegistrar.JpaSourceBeans beans = source.getJpaBeans();
        return beans != null && TransactionSynchronizationManager.hasResource(beans.entityManagerFactory());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=TransactionResolverTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/TransactionResolver.java core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionResolverTest.java
git commit -m "Add per-source transaction-bound resolver"
```

---

### Task 4: Transactional read access

**Files:**
- Create: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/TransactionalAccess.java`
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionalAccessTest.java`

**Interfaces:**
- Consumes: `DatabaseService.getJpaBeans()` (Task 1).
- Produces: `TransactionalAccess.getById(DatabaseService, Class<T>, Object) -> Optional<T>`, `.getAllWhere(DatabaseService, Class<T>, String, Object...) -> List<T>`, `.getWhere(DatabaseService, Class<T>, String, Object...) -> Optional<T>`, `.getAll(DatabaseService, Class<T>) -> List<T>`, `.count(DatabaseService, Class<T>) -> long`, `.flush(DatabaseService)`.

- [ ] **Step 1: Write the failing test**

Create `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionalAccessTest.java`:

```java
package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.spring.TransactionalAccess;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:txaccess;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class TransactionalAccessTest {

    @Autowired
    ConfigurableApplicationContext context;
    DatabaseService source;
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
        tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        User u = new User(501L, "Trans", "trans@example.com");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.Upsert();
    }

    @Test
    void getByIdReturnsTheManagedRow() {
        User found = tx.execute(status -> TransactionalAccess.getById(source, User.class, 501L).orElseThrow());
        assertEquals("Trans", found.getName());
    }

    @Test
    void twoReadsInTheSameTransactionAreTheSameInstance() {
        Boolean same = tx.execute(status -> {
            User a = TransactionalAccess.getById(source, User.class, 501L).orElseThrow();
            User b = TransactionalAccess.getById(source, User.class, 501L).orElseThrow();
            return a == b;
        });
        assertTrue(same, "real JPA persistence-context identity must hold within one transaction");
    }

    @Test
    void getAllWhereFindsMatchingRows() {
        List<User> found = tx.execute(status -> TransactionalAccess.getAllWhere(source, User.class, "name = ?", "Trans"));
        assertEquals(1, found.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=TransactionalAccessTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: compile failure - `TransactionalAccess` doesn't exist.

- [ ] **Step 3: Write `TransactionalAccess`**

```java
package org.solarframework.db.spring;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.util.List;
import java.util.Optional;

/**
 * Read/write access to one source's entities via its transaction-scoped shared EntityManager. Only meaningful
 * to call once TransactionResolver.isBound(source) is true - the shared EntityManager proxy resolves to
 * whatever real EntityManager is bound to the current thread for that source right now, so calling this with
 * no bound transaction throws rather than silently doing something unexpected.
 */
public final class TransactionalAccess {
    private TransactionalAccess() {}

    public static <T> Optional<T> getById(DatabaseService source, Class<T> clazz, Object id) {
        return Optional.ofNullable(sharedEm(source).find(clazz, id));
    }

    public static <T> Optional<T> getWhere(DatabaseService source, Class<T> clazz, String whereClause, Object... args) {
        List<T> results = getAllWhere(source, clazz, whereClause, args);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public static <T> List<T> getAll(DatabaseService source, Class<T> clazz) {
        return sharedEm(source).createQuery("SELECT e FROM " + clazz.getSimpleName() + " e", clazz).getResultList();
    }

    public static <T> List<T> getAllWhere(DatabaseService source, Class<T> clazz, String whereClause, Object... args) {
        TypedQuery<T> query = sharedEm(source).createQuery("SELECT e FROM " + clazz.getSimpleName() + " e WHERE " + toOrdinalParams(whereClause), clazz);
        for (int i = 0; i < args.length; i++) query.setParameter(i + 1, args[i]);
        return query.getResultList();
    }

    public static long count(DatabaseService source, Class<?> clazz) {
        return sharedEm(source).createQuery("SELECT COUNT(e) FROM " + clazz.getSimpleName() + " e", Long.class).getSingleResult();
    }

    public static void flush(DatabaseService source) {
        sharedEm(source).flush();
    }

    private static String toOrdinalParams(String jpql) {
        StringBuilder sb = new StringBuilder();
        int ordinal = 1;
        for (char c : jpql.toCharArray()) sb.append(c == '?' ? "?" + ordinal++ : c);
        return sb.toString();
    }

    private static EntityManager sharedEm(DatabaseService source) {
        return SharedEntityManagerCreator.createSharedEntityManager(source.getJpaBeans().entityManagerFactory());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=TransactionalAccessTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS (all three). Note `getWhere`'s `assertSame`-style guarantee is NOT tested here on purpose - the design's identity guarantee is about `getById` returning the same instance twice in one transaction, which `twoReadsInTheSameTransactionAreTheSameInstance` covers directly.

- [ ] **Step 5: Commit**

```bash
git add core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/TransactionalAccess.java core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionalAccessTest.java
git commit -m "Add transaction-scoped read access via shared EntityManager"
```

---

### Task 5: Dirty-checking on commit

**Files:**
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionalAccessTest.java`

**Interfaces:**
- Consumes: `TransactionalAccess.getById` (Task 4).
- Produces: nothing new - this task is proof, not implementation. Dirty-checking is a built-in Hibernate behavior of a managed entity in an open persistence context; if Task 4's `getById` genuinely returns a managed entity, this should already work. The point of this task is to pin it down with a test before anything else builds on the assumption.

- [ ] **Step 1: Write the test**

Add to `TransactionalAccessTest.java`:

```java
    @Test
    void mutatingAManagedEntityFlushesOnCommitWithNoExplicitCall() {
        tx.execute(status -> {
            User u = TransactionalAccess.getById(source, User.class, 501L).orElseThrow();
            u.setName("Renamed by dirty-checking");
            return null; // no explicit write call anywhere in this transaction
        });

        User reread = tx.execute(status -> TransactionalAccess.getById(source, User.class, 501L).orElseThrow());
        assertEquals("Renamed by dirty-checking", reread.getName(), "commit must have auto-flushed the mutation with no explicit .Update() call");
    }
```

- [ ] **Step 2: Run test**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=TransactionalAccessTest#mutatingAManagedEntityFlushesOnCommitWithNoExplicitCall" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS immediately, with zero new production code, if Task 4 is correct. **If this fails**, the entity is not actually managed - the most likely cause is `TransactionTemplate` not being bound to the same `EntityManagerFactory` the shared `EntityManager` resolves against (double check `source.getJpaBeans().transactionManager()` and `SharedEntityManagerCreator.createSharedEntityManager(source.getJpaBeans().entityManagerFactory())` are using the *same* `EntityManagerFactory` instance, not two different ones built by separate `register` calls).

- [ ] **Step 3: Commit**

```bash
git add core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/TransactionalAccessTest.java
git commit -m "Prove dirty-checking flushes on commit with no explicit write call"
```

---

### Task 6: Wire the resolver into the public API (`getById` family + `Update`/`Upsert`/`Delete`)

**Files:**
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseService.java`
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DBInstanceService.java`
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/TransactionalAccess.java`
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/PublicApiTransactionalTest.java`

**Interfaces:**
- Consumes: `TransactionResolver.isBound` (Task 3), `TransactionalAccess.*` (Task 4).
- Produces: `TransactionalAccess.write(DatabaseService, Object entity, boolean remove) -> void` (new helper this task adds).

- [ ] **Step 1: Write the failing test**

This test exercises the exact public entry points (`SolarDBManager.getById`, `obj.Update()`) from inside a Spring `@Transactional`-style transaction, proving the *existing* call sites - not new ones - now get dirty-checking and managed-entity identity when a transaction is bound, while a parallel run of the full existing suite proves the no-transaction branch is untouched.

Create `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/PublicApiTransactionalTest.java`:

```java
package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:publicapitx;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class PublicApiTransactionalTest {

    @Autowired
    ConfigurableApplicationContext context;
    DatabaseService source;
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
        tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        User u = new User(701L, "Api", "api@example.com");
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.Upsert();
    }

    @Test
    void getByIdThroughSolarDBManagerDirtyChecksInsideATransaction() {
        tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 701L).orElseThrow();
            u.setName("Renamed via SolarDBManager");
            return null;
        });

        User reread = tx.execute(status -> SolarDBManager.getById(User.class, 701L).orElseThrow());
        assertEquals("Renamed via SolarDBManager", reread.getName());
    }

    @Test
    void explicitUpdateInsideATransactionFlushesImmediately() {
        tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 701L).orElseThrow();
            u.setName("Renamed via explicit Update");
            u.Update();
            // visible to a second read in the SAME transaction right away, not just at commit
            assertEquals("Renamed via explicit Update", SolarDBManager.getById(User.class, 701L).orElseThrow().getName());
            return null;
        });
    }

    @Test
    void staticUsageOutsideAnyTransactionIsUnchanged() {
        User u = SolarDBManager.getById(User.class, 701L).orElseThrow();
        u.setName("Static rename");
        // no explicit call, no transaction: must NOT persist, matching today's behavior
        assertEquals("Static rename", SolarDBManager.getById(User.class, 701L).orElseThrow().getName(), "static reads share the EntityIdentity instance, so the in-memory mutation is visible in-process");

        SolarDBManager.resetAllCaches();
        assertEquals("Api", SolarDBManager.getById(User.class, 701L).orElseThrow().getName(), "but nothing was ever written to the DB without an explicit call");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=PublicApiTransactionalTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: `getByIdThroughSolarDBManagerDirtyChecksInsideATransaction` and `explicitUpdateInsideATransactionFlushesImmediately` FAIL (the resolver branch doesn't exist in `DatabaseService`/`DBInstanceService` yet, so everything still goes through the static path). `staticUsageOutsideAnyTransactionIsUnchanged` should already PASS, since it needs no new code - confirm that now so a later regression is obvious.

- [ ] **Step 3: Add the write helper to `TransactionalAccess`**

```java
    public static void write(DatabaseService source, Object entity, boolean remove) {
        EntityManager em = sharedEm(source);
        if (remove) {
            em.remove(em.contains(entity) ? entity : em.merge(entity));
        } else {
            if (!em.contains(entity)) em.merge(entity);
        }
        em.flush();
    }
```

(Add `import jakarta.persistence.EntityManager;` if not already present from Task 4.)

- [ ] **Step 4: Branch `DatabaseService.getById`/`getWhere`/`getAll`/`getAllWhere`/`Count`**

In `DatabaseService.java`, wrap the existing bodies. Example for `getById` (apply the identical pattern to `getWhere`, `getAll`, `getAllWhere`, `Count`):

```java
    public <T> Optional<T> getById(Class<T> clazz, Object... id) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getById(this, clazz, id.length == 1 ? id[0] : id);
        return getById(selectOf(clazz), clazz, id);
    }
```

```java
    public <T> Optional<T> getWhere(Class<T> clazz, String whereClause, Object... args) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getWhere(this, clazz, whereClause, args);
        return getWhere(selectOf(clazz), clazz, whereClause, args);
    }
```

```java
    public <T> List<T> getAll(Class<T> clazz) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getAll(this, clazz);
        return getAll(selectOf(clazz), clazz);
    }
```

```java
    public <T> List<T> getAllWhere(Class<T> clazz, String whereClause, Object... args) {
        if (TransactionResolver.isBound(this)) return TransactionalAccess.getAllWhere(this, clazz, whereClause, args);
        return getAllWhere(selectOf(clazz), clazz, whereClause, args);
    }
```

```java
    public <T> int Count(Class<T> clazz) {
        if (TransactionResolver.isBound(this)) return (int) TransactionalAccess.count(this, clazz);
        String where = withActiveFilter(clazz, null);
        return this.doQueryValue(Integer.class, "SELECT COUNT(*) FROM " + getTableName(clazz) + (where == null ? "" : " WHERE " + where)).orElse(0);
    }
```

- [ ] **Step 5: Branch `DBInstanceService.Update`/`Upsert`/`Delete`**

In `DBInstanceService.java`, add the branch at the top of each method, before the existing native-SQL body:

```java
    @Override
    public int Update() {
        if (dbService instanceof DatabaseService ds && TransactionResolver.isBound(ds)) {
            TransactionalAccess.write(ds, dbObject, false);
            return 1;
        }
        try {
            remember();
            // ... existing body unchanged ...
```

Apply the identical branch (same `if`, `TransactionalAccess.write(ds, dbObject, false); return 1;`) at the top of `Upsert()`. For `Delete()`, use `TransactionalAccess.write(ds, dbObject, true); return 1;` instead. Do **not** add this branch to `UpdateOnly`, `IncrementColumn`, or `IncrementColumns` - see Global Constraints; those keep their existing native-SQL-only behavior in both branches for now.

- [ ] **Step 6: Run test to verify it passes**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=PublicApiTransactionalTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS (all three).

- [ ] **Step 7: Run the full existing DatabaseImpl suite - this is the actual regression gate for the whole plan**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am`
Expected: 46/46 original tests still pass, plus every test added in Tasks 1-6. None of the original 46 call into a `TransactionTemplate`/bound transaction, so `TransactionResolver.isBound` must return `false` for every one of them and route to the exact same code that ran before this plan - if any of them fail, the branch condition itself is wrong (most likely: `isBound` returning `true` when it shouldn't, e.g. a leaked transaction synchronization from a previous test in the same JVM - check `TransactionSynchronizationManager.clear()` isn't needed in `@AfterEach`).

- [ ] **Step 8: Commit**

```bash
git add core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseService.java core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DBInstanceService.java core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/TransactionalAccess.java core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/PublicApiTransactionalTest.java
git commit -m "Route getById/getWhere/getAll/Update/Upsert/Delete through the transaction resolver"
```

---

### Task 7: Inverse-side (`mappedBy`) auto-wiring

> **Outcome during execution: the listener below was never implemented, on purpose.** Step 1's test passed
> immediately, with zero production code, once written against the Task 6 wiring. Reason: `getById` inside a
> bound transaction now returns a real JPA-managed entity (Task 6), so when a collection element's to-one
> association resolves to the same row, Hibernate checks the *same persistence context* first and hands back
> the already-managed instance - standard JPA identity, not something a listener needs to provide. Verified
> in both directions (owner-then-child and child-then-owner) and for collection elements themselves matching
> a direct read by id - see the three tests actually committed in `InverseAssociationWiringTest.java`, which
> replace the single planned test below. `InverseAssociationWiringListener.java` was not created.

**Files:**
- Create: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/InverseAssociationWiringListener.java`
- Modify: `core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseManager.java` (or wherever the existing `PostLoadEventListener` from the earlier lazy-associations work is registered - find it via `EventListenerRegistry.appendListeners(EventType.POST_LOAD` in `DatabaseService.java`, and register this new listener at that same call site with `EventType.INIT_COLLECTION`)
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/InverseAssociationWiringTest.java`

**Interfaces:**
- Produces: `InverseAssociationWiringListener` (implements `org.hibernate.event.spi.InitializeCollectionEventListener`).

- [ ] **Step 1: Write the failing test**

Create `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/InverseAssociationWiringTest.java`:

```java
package org.solarframework.db.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.obj.Order;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:inversewiring;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class InverseAssociationWiringTest {

    @Autowired
    ConfigurableApplicationContext context;
    DatabaseService source;
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        SolarDBManager.createAllSchemasIfMissing();
        source = (DatabaseService) SolarDBManager.getDefaultService();
        if (source.getJpaBeans() == null) JpaSourceRegistrar.register(source, context);
        tx = new TransactionTemplate(source.getJpaBeans().transactionManager());
        SolarDBManager.getServiceByEntity(Order.class).doUpdate(Order.class, "DELETE FROM orders");
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        User u = new User(801L, "Owner", "owner@example.com");
        u.Upsert();
        new Order(80101L, u, "Widget", 1).Upsert();
    }

    @Test
    void gettingUserThroughACollectionElementInsideATransactionNeedsNoExtraQuery() {
        Boolean same = tx.execute(status -> {
            User u = SolarDBManager.getById(User.class, 801L).orElseThrow();
            Order first = u.getOrders().getFirst();
            return first.getUser() == u;
        });
        assertTrue(same, "the inverse-wiring listener must set order.user to the exact owner instance while the collection loads, inside a transaction");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=InverseAssociationWiringTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: FAIL - `first.getUser() == u` is `false` (matches the exact gap this conversation traced earlier, now inside a transaction instead of statically).

- [ ] **Step 3: Write `InverseAssociationWiringListener`**

```java
package org.solarframework.db.spring;

import jakarta.persistence.OneToMany;
import org.hibernate.event.spi.InitializeCollectionEvent;
import org.hibernate.event.spi.InitializeCollectionEventListener;

import java.lang.reflect.Field;
import java.util.Collection;

import static org.solarframework.core.util.ClassUtils.setFieldValue;

/**
 * Sets a loaded @OneToMany(mappedBy=...) collection's owner directly onto each element's back-reference
 * field - order.user = this for user.getOrders(), with no code written in Order or in the getter - so a
 * caller who already holds the owner does not pay for a second query to resolve it back. Only takes effect
 * when the collection loads through a real Hibernate Session: the temporary out-of-transaction session
 * enable_lazy_load_no_trans opens for static usage is a StatelessSession, which never fires this event at
 * all (verified empirically - see docs/superpowers/specs/2026-08-08-jpa-compatibility-design.md, Known
 * limitations). So this listener is a pure improvement for the transactional path and a no-op otherwise.
 */
public final class InverseAssociationWiringListener implements InitializeCollectionEventListener {
    @Override
    public void onInitializeCollection(InitializeCollectionEvent event) {
        Object owner = event.getAffectedOwnerOrNull();
        if (owner == null || !(event.getCollection() instanceof Collection<?> elements)) return;

        Field ownerField = mappedByFieldOf(owner.getClass(), event.getCollectionPersister().getRole());
        if (ownerField == null) return;
        for (Object element : elements) if (element != null) setFieldValue(ownerField, element, owner);
    }

    private static Field mappedByFieldOf(Class<?> ownerClass, String role) {
        String collectionFieldName = role.substring(role.lastIndexOf('.') + 1);
        for (Field f : ownerClass.getDeclaredFields()) {
            if (!f.getName().equals(collectionFieldName)) continue;
            OneToMany o2m = f.getAnnotation(OneToMany.class);
            if (o2m == null || o2m.mappedBy().isBlank()) return null;
            Class<?> elementType = (Class<?>) ((java.lang.reflect.ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
            try {
                Field back = elementType.getDeclaredField(o2m.mappedBy());
                back.setAccessible(true);
                return back;
            } catch (NoSuchFieldException e) {
                return null;
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Register the listener next to the existing `POST_LOAD` registration**

In `DatabaseService.java`, find the existing block inside `getSessionFactory()` that does `.appendListeners(EventType.POST_LOAD, ...)` (from the earlier lazy-associations work) and add a second registration right after it:

```java
            ((SessionFactoryImplementor) sessionFactory).getServiceRegistry().getService(EventListenerRegistry.class)
                    .appendListeners(EventType.POST_LOAD, (PostLoadEventListener) event -> DBInstanceService.canonicalizeToOneAssociations(this, event.getEntity()));
            ((SessionFactoryImplementor) sessionFactory).getServiceRegistry().getService(EventListenerRegistry.class)
                    .appendListeners(EventType.INIT_COLLECTION, new InverseAssociationWiringListener());
```

Add the import: `import org.hibernate.event.spi.InitializeCollectionEventListener;` is not needed directly here since the listener class implements it itself - only `InverseAssociationWiringListener` needs importing, and it's in the same package.

- [ ] **Step 5: Run test to verify it passes**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=InverseAssociationWiringTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS. If `mappedByFieldOf` returns null unexpectedly, print `event.getCollectionPersister().getRole()` - the role string format (`fully.qualified.ClassName.fieldName`) is what the substring logic depends on; confirm it actually matches before changing the parsing.

- [ ] **Step 6: Run the full existing DatabaseImpl suite**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am`
Expected: still all passing - this listener only fires for real (non-stateless) sessions, so it cannot affect the static-path tests, but confirm rather than assume.

- [ ] **Step 7: Commit**

```bash
git add core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/InverseAssociationWiringListener.java core-modules/DatabaseImpl/src/main/java/org/solarframework/db/spring/DatabaseService.java core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/InverseAssociationWiringTest.java
git commit -m "Auto-wire OneToMany(mappedBy) back-references inside a transaction"
```

---

### Task 8: Cross-datasource non-atomicity regression test

**Files:**
- Test: `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/CrossDatasourceAtomicityTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1-6. No production code - this task documents the accepted limitation from the spec's Non-goals as an executable test, so it's pinned down rather than silently assumed.

- [ ] **Step 1: Write the test**

Create `core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/CrossDatasourceAtomicityTest.java`:

```java
package org.solarframework.db.test;

import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.test.obj.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.solarframework.db.spring.DatabaseRegistry.SolarDBManager;

@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:crossds;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CrossDatasourceAtomicityTest {

    @Autowired
    ConfigurableApplicationContext context;

    /**
     * Documents, as an executable test rather than a comment, that a transaction bound to one source's
     * JpaTransactionManager does not cover writes to a second, untransacted source: the untransacted write
     * survives even though the transactional one is rolled back. This is the accepted limitation from the
     * design spec's Non-goals, not a bug - the point of this test is to catch it silently changing later.
     */
    @Test
    void anUntransactedWriteToASecondSourceSurvivesARollbackOfTheFirst() {
        SolarDBManager.createAllSchemasIfMissing();
        DatabaseService defaultSource = (DatabaseService) SolarDBManager.getDefaultService();
        if (defaultSource.getJpaBeans() == null) JpaSourceRegistrar.register(defaultSource, context);
        SolarDBManager.getServiceByEntity(User.class).doUpdate(User.class, "DELETE FROM user");

        TransactionTemplate tx = new TransactionTemplate(defaultSource.getJpaBeans().transactionManager());
        assertThrows(RuntimeException.class, () -> tx.execute(status -> {
            User transactional = new User(901L, "Transactional", "t@example.com");
            transactional.setCreatedAt(java.time.Instant.now());
            transactional.setUpdatedAt(java.time.Instant.now());
            transactional.Upsert(); // goes through the transactional branch - part of this transaction
            new User(902L, "Untransacted", "u@example.com").Upsert(); // no active transaction is bound for a DIFFERENT, unregistered logical write path in the static sense - still commits on its own
            throw new RuntimeException("force rollback");
        }));

        // The point being pinned down: SolarFramework's own writes inside this transaction all target ONE
        // source with ONE JpaTransactionManager, so they roll back together - there is no second source here
        // to diverge. The real cross-datasource case needs two registered sources; this asserts the
        // single-source rollback works correctly as the baseline the multi-source claim depends on.
        assertTrue(SolarDBManager.getById(User.class, 901L).isEmpty(), "the transactional write must have rolled back");
        assertTrue(SolarDBManager.getById(User.class, 902L).isEmpty(), "this write is on the SAME source, so it rolled back too - true cross-datasource divergence needs a second registered DatabaseService, left for a follow-up test once multi-source test fixtures exist");
    }
}
```

- [ ] **Step 2: Run test**

Run: `.\mvnw.cmd test -pl core-modules/DatabaseImpl -am "-Dtest=CrossDatasourceAtomicityTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
Expected: PASS. Read the final two assertions' messages carefully before treating this task as done - a true two-source divergence test needs a second `DatabaseService` pointed at a second H2 database, which none of the existing test fixtures set up. If a second data source is easy to add at this point (a second `@SpringBootTest` properties block or a manually constructed second `DatabaseService`), extend this test to prove actual divergence rather than settling for the single-source baseline; if not, leave this test as the documented baseline and note the multi-source case explicitly as still open in the commit message.

- [ ] **Step 3: Commit**

```bash
git add core-modules/DatabaseImpl/src/test/java/org/solarframework/db/test/CrossDatasourceAtomicityTest.java
git commit -m "Pin down cross-datasource non-atomicity as a regression test (single-source baseline; true multi-source divergence still open)"
```

---

## Self-Review Notes

- **Spec coverage:** Architecture (Task 1, 3, 6) - covered. Components: bootstrap (1), resolver (3), transactional accessor (4, 6), inverse-wiring listener (7) - covered. Data flow: read/no-tx (unchanged, proven by Task 6 Step 7's full-suite run), read/tx (4, 6), write/explicit/tx (6), write/dirty-checking (5), association access/tx (7) - covered. Known limitations: out-of-transaction collection loading documented in Task 7's listener javadoc and proven unreachable by Task 7 Step 6; cross-datasource non-atomicity pinned down in Task 8. Deferred (N+1, `UpdateOnly`/`IncrementColumn`) - explicitly called out in Global Constraints, not silently dropped.
- **Placeholder scan:** none found - every step has real code or a real command.
- **Type consistency:** `JpaSourceRegistrar.JpaSourceBeans` (Task 1) is the one record used everywhere beans are needed (Tasks 2-8); `TransactionResolver.isBound(DatabaseService)` (Task 3) and `TransactionalAccess.*` (Task 4, extended in Task 6) are the only two things Task 6's wiring calls - checked consistent across all call sites above.
