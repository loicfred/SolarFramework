package org.solarframework.db.test.transactional;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.solarframework.db.spring.DatabaseService;
import org.solarframework.db.spring.JpaSourceRegistrar;
import org.solarframework.db.spring.TransactionResolver;
import org.solarframework.db.test.Database_Main;
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
        // Not asserting source.getJpaBeans() == null here on purpose: that getter lazily rebuilds on access
        // (see registrationIsLazyUntilSomethingActuallyAsksForIt below), so calling it would immediately
        // build a fresh one rather than observe the torn-down state - TransactionResolver.isBound, which
        // reads the raw field, is the correct way to observe "nothing built yet" from outside this package.
        assertFalse(TransactionResolver.isBound(source), "no transaction is open, so this must read as unbound regardless of whether beans exist");
    }

    @Test
    void registrationIsLazyUntilSomethingActuallyAsksForIt() {
        DatabaseService source = (DatabaseService) SolarDBManager.getDefaultService();
        JpaSourceRegistrar.unregister(source, context); // start from a clean slate regardless of prior tests in this class

        assertFalse(TransactionResolver.isBound(source), "isBound must never itself trigger the lazy build - a source nothing uses should stay unregistered");
        assertFalse(context.getBeanFactory().containsSingleton(JpaSourceRegistrar.beanNameOf(source) + "_emf"), "no EntityManagerFactory should exist yet");

        JpaSourceRegistrar.JpaSourceBeans beans = source.getJpaBeans(); // the one call that IS allowed to build it

        assertNotNull(beans);
        assertTrue(context.getBeanFactory().containsSingleton(JpaSourceRegistrar.beanNameOf(source) + "_emf"), "getJpaBeans() must have built it on this call");
    }
}
