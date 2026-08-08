# JPA compatibility: real @Transactional and dirty-checking, without losing static usage

Date: 2026-08-08
Scope: SolarFramework — `DatabaseAPI`, `DatabaseImpl` (persistence core). Entity classes in every other
module (TournamentAPI/Impl, Discord, ProxyServer, DatabaseEditor, ...) are not expected to change.

## Problem

`DatabaseService` builds its own `SessionFactory`/`DataSource` by hand (`StandardServiceRegistryBuilder`,
manual connection pooling) and runs every operation through a brand-new `EntityManager` that opens, does
one thing, and closes (`DatabaseUtils.withEm`/`inTransaction`). This never participates in Spring's
`PlatformTransactionManager`/`@Transactional` machinery, because Spring has no bean to bind a transaction
to — the whole stack is invisible to it. Two consequences:

- A host application's own `@Transactional` service methods cannot get real dirty-checking against
  SolarFramework entities: mutating a field and returning does nothing, because the object is already
  detached by the time it's handed back.
- `SolarDBManager.getById`/`getWhere`/etc. and the object's own `.Update()`/`.Upsert()`/`.Delete()` are the
  framework's real value — multiple data sources, addable at runtime, and write-from-the-object-itself
  ergonomics — and none of that should have to change for callers who don't care about `@Transactional`.

## Goals

- `SolarDBManager.getById`/`getWhere`/`getAll`/`getAllWhere`/`Count`/etc., and `DatabaseObject`'s own
  `.Upsert()`/`.Update()`/`.UpdateOnly()`/`.Delete()`/`.IncrementColumn()`, keep their exact signatures and
  call sites. No entity class in any other module should need to change.
- Called with no ambient transaction ("static" usage, exactly as today): behavior is unchanged, byte-for-byte
  — same short-lived-`EntityManager`-per-call mechanics, same `EntityIdentity` global-identity guarantee.
- Called from inside an active transaction — either one SolarFramework itself opens, or a host application's
  own `@Transactional` method, whether it obtained its `SolarDBManager`/entity reference statically or via
  autowiring — real JPA semantics apply: managed entities, real dirty-checking, real persistence-context
  identity.
- Multiple data sources, including ones registered at runtime via `DatabaseManager.addSource()`/
  `makeNewSource()`, keep working under both usage styles.

## Non-goals

- Cross-datasource atomicity (JTA/XA). A transaction covers writes to the one source its transaction manager
  is bound to; touching a second, untransacted source inside that scope is a separate, independently
  committed operation. This is the same limitation every Spring multi-`DataSource` application has without a
  JTA transaction manager, and out of scope here.
- N+1 / eager-collection batch-fetching (e.g. `IParticipant.members` issuing one query per parent row). Real,
  separate problem, same entity-mapping layer, deliberately deferred to its own follow-up.
- Bytecode enhancement / no-proxy lazy loading.

## Architecture

Each registered data source gains a real `LocalContainerEntityManagerFactoryBean` + `JpaTransactionManager`,
built programmatically (not via Spring Boot's static startup autoconfiguration, since sources can be added
at runtime) and registered into the live `ApplicationContext`.

Every entry point routes through one shared resolver:

1. Resolve the entity's owning source (the same lookup `getServiceByEntity` already does today).
2. Check whether a transaction is already bound to the *current thread*, for *that specific source's*
   `JpaTransactionManager` (`TransactionSynchronizationManager`, keyed per source — there can be several
   bound at once, one per source, entirely independent of each other).
3. Branch:
   - **Bound** → use a transaction-scoped shared `EntityManager` for that source
     (`SharedEntityManagerCreator.createSharedEntityManager(emf)`, which resolves to whatever `EntityManager`
     is currently bound to the thread for that `EntityManagerFactory`). Real managed entities, real
     dirty-checking, real persistence-context identity.
   - **Not bound** → fall through to today's existing code, completely unchanged: open a short-lived
     `EntityManager`+transaction, do the one thing, commit, close; identity backed by `EntityIdentity`.

This one branch point is the entire mechanism. It's what makes static calls and autowired/`@Transactional`
calls work through the same code without separate implementations for each usage style — Spring binds
transactions to the thread, not to how the caller obtained the reference, so "static" and "autowired" are
really the same case (no bound transaction / bound transaction) rather than two different code paths.

Branch selection happens fresh at every call, not cached from how an object was originally obtained: an
object read via the static branch, later mutated and written while a transaction happens to be active for
its source, uses the transactional branch at write time. That means standard JPA `merge()` semantics apply
at that point — you get back a managed copy, not the original instance mutated in place. This is a visible
behavior difference from "always the same instance," but it's correct JPA behavior, and consistent with the
identity decision below.

## Components

**New**

- *Per-source JPA bootstrap* (replaces `DatabaseService.getSessionFactory()`'s manual
  `StandardServiceRegistryBuilder` call): builds a `LocalContainerEntityManagerFactoryBean` +
  `JpaTransactionManager` for a source and registers them as beans. Invoked both at startup for configured
  sources and from `DatabaseManager.addSource()`/`makeNewSource()` for runtime-added ones, with matching
  teardown on removal.
- *Transaction resolver*: the "is a transaction already bound to this specific source, on this thread?"
  check described above. The one thing every read/write entry point calls through.
- *Transactional accessor*: a small, separate implementation of the existing read/write surface, backed by
  the shared `EntityManager` — `entityManager.find(clazz, id)` for `getById`, JPQL/criteria for
  `getWhere`/`getAll`, and plain managed-entity semantics for writes. Resolved once per call, the same way
  `getServiceByEntity` resolves *which source* today — not an if/else duplicated inside every method.
- *Inverse-side auto-wiring listener*: a generic, reflection-driven `InitializeCollectionEventListener`,
  registered once per source. Reads a loaded `@OneToMany(mappedBy=...)` collection's owner and its `mappedBy`
  field name, and sets that field directly on each loaded child — `order.user = this` for
  `user.getOrders()`, with no code written in `getOrders()` or `Order` itself. Only fires when the collection
  loads through a real Hibernate `Session` (see Known limitations).

**Unchanged**

`DatabaseService`'s existing `withEm`/`inTransaction`, `DBInstanceService`'s reflection-based native-SQL
writes, `EntityIdentity`, and the existing to-one-canonicalization `PostLoadEventListener` all stay exactly
as they are today. They *are* the "not bound" branch of the resolver, verbatim. Since Hibernate's own
persistence context already gives correct identity for anything loaded inside an active transaction, none of
that machinery is needed there — it only ever mattered for the static case, and the static case doesn't
change.

**Modified**

`SolarDBManager`'s read methods and `DatabaseObject`'s write methods gain the resolver branch: bound → use
the transactional accessor; not bound → fall through to the existing implementation unchanged.

## Data flow

**Read, no transaction** (`SolarDBManager.getById(User.class, 1)`, called from anywhere): identical to
today. Resolver finds nothing bound for `User`'s source, opens a short-lived `EntityManager`+transaction,
runs the native query, closes it, returns a detached object registered in `EntityIdentity`. Same instance on
repeat calls.

**Read, inside a transaction**: resolver finds the bound transaction, uses the shared `EntityManager`.
Returns a real managed entity. A second `getById` for the same row anywhere else in that same transaction
returns the same managed instance — real persistence-context identity.

**Write, explicit call, no transaction**: unchanged, builds and runs the native `UPDATE` immediately.

**Write, explicit call, inside a transaction**: the object is already managed and dirty; the call flushes
just that entity immediately, then returns. This matches what an explicit call means today (the write is
done, visible to any other query in the same transaction from that point on) rather than silently downgrading
to "trust dirty-checking to catch it eventually at commit" — dirty-checking still independently catches any
*other* unflushed changes at commit as normal.

**Write, dirty-checking only, no explicit call**: no transaction → nothing happens, matches today. Inside a
transaction → Hibernate's own dirty-checking flushes the change at commit. This is the new capability.

**Association access** (`order.getUser()`, `user.getOrders()`): no transaction → unchanged; to-one fields
canonicalize via `EntityIdentity` where possible, collection-fault back-references still don't auto-wire
(see Known limitations). Inside a transaction → both listeners fire on a real session: to-one fields resolve
to the transaction's managed instances, and `order.user` is set directly by the inverse-wiring listener when
loaded via `user.getOrders()` — no extra query for `getUser()` afterward.

## Known limitations

- **No cross-datasource atomicity** (see Non-goals). Needs its own explicit test, not just documentation, so
  it's pinned down as expected behavior rather than discovered as a surprise.
- **Out-of-transaction collection/proxy loading still can't be hooked.** Lazy loading with no ambient
  transaction goes through Hibernate's `enable_lazy_load_no_trans` mechanism, which opens a `StatelessSession`
  specifically to fault the collection or proxy — and `StatelessSession` skips Hibernate's entire event
  system by design (confirmed empirically: zero `PostLoad`/`INIT_COLLECTION` firings during that path).
  Neither the to-one canonicalization nor the new inverse-wiring listener can reach that path. Both only take
  effect when a real, non-stateless session is active — i.e., inside a transaction.
- **Runtime source removal mid-use** needs an explicit test rather than an assumption that in-flight
  operations finish safely once a source's beans are deregistered.

## Testing & migration

- All 46 existing `DatabaseImpl` tests continue running against the unchanged "not bound" branch — they
  become regression tests for "did this leave the untouched path untouched."
- New tests for the transactional branch: dirty-checking without an explicit call, explicit calls inside a
  transaction, the inverse-wiring listener, and the cross-datasource non-atomicity case.
- Runtime source add/remove gets its own test: add a source, do work against it both inside and outside a
  transaction, remove it, confirm no leaks or errors.

## Deferred follow-ups

- N+1 / eager-collection batch-fetching (`@BatchSize`, switching specific associations to `LAZY`, or
  join-fetching specific query paths) — real and currently visible in production logs, but a separate
  mapping-strategy problem orthogonal to this spec.
