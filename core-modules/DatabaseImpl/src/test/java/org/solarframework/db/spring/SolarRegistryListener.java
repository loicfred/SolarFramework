package org.solarframework.db.spring;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Points the static {@link DatabaseRegistry} - and every static cache keyed off it - at the context the test
 * about to run actually belongs to. Lives in the framework's own package so it can reach those caches.
 *
 * <p>Without this the whole module needs a fork per test class: the registry is static and Spring hands out a
 * cached context without re-running its bean constructors, so the one class on MariaDB would leave every later
 * H2 class talking to the remote server. With it, one JVM and one context per distinct configuration is enough,
 * which is why {@code reuseForks} is left at its default. The identity check keeps this free for the common
 * case - all classes sharing {@link org.solarframework.db.test.SolarH2Test} hit it once and never again.
 */
public class SolarRegistryListener extends AbstractTestExecutionListener {

    @Override
    public void prepareTestInstance(TestContext testContext) {
        DatabaseManager dm = testContext.getApplicationContext().getBean(DatabaseManager.class);
        if (DatabaseRegistry.SolarDBManager == dm) return;

        // Same set DatabaseManager#reload clears, minus the classpath rescan: the incoming manager already
        // knows its entities, what is stale is everything the outgoing one memoised about them.
        DatabaseObject.TableNames.clear();
        DatabaseObject.serviceCache.clear();
        DatabaseObject.entityServiceCache.clear();
        DBInstanceService.IdFields.clear();
        DBInstanceService.CachedFields.clear();
        DBInstanceService.LazyFields.clear();
        DBInstanceService.ToOneFields.clear();
        DBInstanceService.OneToManyFields.clear();
        DatabaseService.clearLazySelects();
        EntityIdentity.clear();

        // Before getDefaultService(): DatabaseService#isDefault compares against this static, so while it
        // still holds the outgoing context's URL every source of the incoming one reads as non-default.
        DatabaseConfig.defaultConnectionString = testContext.getApplicationContext().getBean(DatabaseService.class).getConnectionString();
        DatabaseRegistry.SolarDBManager = dm;
        DatabaseRegistry.DefaultDBService = dm.getDefaultService();
    }
}
