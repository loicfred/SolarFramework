# Collection associations stopped loading

Date: 2026-08-03, resolved 2026-08-04
Scope: SolarFramework — `DatabaseImpl` (one line), plus documentation

> This document originally specified a three-state sentinel (`Lazy.UNLOADED_LIST` + a derived
> `refetchAssociation`) on the premise that Hibernate could not fault a detached collection here. **That
> premise was wrong** and the design was abandoned mid-implementation. What follows is what the problem
> actually was. The sentinel work is preserved only in git history.

## Symptom

`Discord_Profile.getVariables()` and `Discord_GuildInfo.getRoles()/getChannels()/getVariables()` — all of
the form `x == null ? x = new ArrayList<>() : x` — returned empty for every persisted row. Every read of a
profile or guild variable fell through to its `.orElse(...)` default, and `getUsageChannel`/`getUsageRole`
resolved to `Optional.empty()`, so the log, notification and clan-updates channels, the winner roles and the
clan captain role all read as unset. Writes were unaffected: the rows were correct in the database and
simply never read back.

## Root cause

One line, `DBInstanceService.dropPlaceholders`:

```java
for (Field f : assocFieldsOf(obj.getClass())) if (!isLoaded(getFieldValue(f, obj))) setFieldValue(f, obj, null);
```

It runs on every entity read (`DatabaseService.java:617`) and nulls each association Hibernate hydrated but
did not load. For a single-valued association that is right — a dead proxy would only blow up on whoever
touched it. For a **collection** it destroys the `PersistentBag`, and the bag is the loading mechanism: with
`hibernate.enable_lazy_load_no_trans=true` (`DatabaseService.java:271`) it faults itself on first access even
though `withEm` closed the EntityManager when the query returned. Nulled, the getter took its `== null`
branch — the branch meant for a hand-built object — and reported empty.

`dropPlaceholders` and `EntityIdentity` were introduced in the same commit (`e8a7c56`); before it,
association loading worked, which matches what the author remembered seeing.

### What was wrong in the original analysis

Two assumptions were tested and both failed:

1. *"A detached bag throws `no entry for collection`."* It does not. With the bag left in place, a fetched
   `User` carries a real `PersistentBag`, `wasInitialized()` is false, and first access loads both children.
2. *"`EntityIdentity.canonical` moving a bag to another instance breaks it."* It does not either. Copying the
   bag onto the canonical object and then reading it works, with no exception.

The lesson is narrow: `enable_lazy_load_no_trans` was doing its job all along, and nothing needed to replace
it. The framework has no persistence context, but it does not need one for this.

## Fix

Skip collections:

```java
for (Field f : assocFieldsOf(obj.getClass())) if (!Collection.class.isAssignableFrom(f.getType()) && !isLoaded(getFieldValue(f, obj))) setFieldValue(f, obj, null);
```

No entity, getter, annotation or `fetch` mode changes. The getters were already correct.

## Regression guards

`DatabaseIdentityTest`, in `core-modules/DatabaseImpl`:

- `aReadHandsBackItsChildren` — a fetched user reports the orders it owns. Replaces
  `aReadHandsBackNoChildren`, which asserted the broken behaviour.
- `aReadLeavesAFaultableBag…` — the field holds a `PersistentCollection`, it has *not* faulted yet, and
  first access returns both children. This is the one that fails if `dropPlaceholders` ever nulls
  collections again.
- `aHandBuiltObjectStillStartsWithAnEmptyMutableList` — the `== null` branch still gives a growable list, so
  an object graph can be assembled before it is saved.

Suite: 46 passing, up from 44. `DatabaseBlobTest` unchanged at 11 — the `byte[]` path was never involved.

## Known limitations, unchanged by this fix

- Entities read through the `Tuple` path (a partial-column select, not `SELECT *`) get no bag, so their
  collections stay `null` and report empty. No caller does this with an association today.
- `invalidateVariables()`/`invalidateCaches()` null the field, which now yields an empty list rather than a
  re-read. Both are unused; re-`get()` the entity instead.
