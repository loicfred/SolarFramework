package org.solarframework.db.spring;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import jakarta.persistence.spi.PersistenceUnitInfo;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.SpringPersistenceUnitInfo;
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
        // Raw field read, not the getJpaBeans() getter - that getter calls back into register() when the
        // field is null, which would recurse forever on exactly the call this check exists to short-circuit.
        if (source.jpaBeans != null) return source.jpaBeans;
        SpringPersistenceUnitInfo mutableInfo = new SpringPersistenceUnitInfo(source.getClass().getClassLoader());
        mutableInfo.setPersistenceUnitName(source.jpaBeanName());
        mutableInfo.setPersistenceProviderClassName(HibernatePersistenceProvider.class.getName());
        mutableInfo.setNonJtaDataSource(source.getDataSource());
        for (Class<?> c : source.getEntitiesClasses()) mutableInfo.addManagedClassName(c.getName());
        mutableInfo.setExcludeUnlistedClasses(true);
        PersistenceUnitInfo unitInfo = mutableInfo.asStandardPersistenceUnitInfo();

        PersistenceUnitManager unitManager = new PersistenceUnitManager() {
            @Override
            public PersistenceUnitInfo obtainDefaultPersistenceUnitInfo() {
                return unitInfo;
            }
            @Override
            public PersistenceUnitInfo obtainPersistenceUnitInfo(String persistenceUnitName) {
                return unitInfo;
            }
        };

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setPersistenceUnitManager(unitManager);
        emfBean.setDataSource(source.getDataSource());
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", source.getDatabaseType().getDialectClass());
        emfBean.setJpaPropertyMap(props);
        emfBean.afterPropertiesSet();

        EntityManagerFactory emf = emfBean.getObject();
        JpaTransactionManager txManager = new JpaTransactionManager(emf);

        // DatabaseService#reload() nulls jpaBeans (the DataSource it was built around just closed) but has
        // no access to the ApplicationContext to also drop the stale bean registration - clean up here
        // defensively so a re-register after reload() doesn't collide with the orphaned singleton.
        var beanFactory = (org.springframework.beans.factory.support.DefaultListableBeanFactory) context.getBeanFactory();
        String emfName = source.jpaBeanName() + "_emf";
        String txName = source.jpaBeanName() + "_txManager";
        if (beanFactory.containsSingleton(emfName)) beanFactory.destroySingleton(emfName);
        if (beanFactory.containsSingleton(txName)) beanFactory.destroySingleton(txName);
        beanFactory.registerSingleton(emfName, emf);
        beanFactory.registerSingleton(txName, txManager);

        JpaSourceBeans beans = new JpaSourceBeans(emf, txManager);
        source.setJpaBeans(beans);
        return beans;
    }

    public static void unregister(DatabaseService source, ConfigurableApplicationContext context) {
        JpaSourceBeans beans = source.jpaBeans; // raw field - nothing to tear down should never trigger a build just to check
        if (beans == null) return;
        var beanFactory = (org.springframework.beans.factory.support.DefaultListableBeanFactory) context.getBeanFactory();
        beanFactory.destroySingleton(source.jpaBeanName() + "_emf");
        beanFactory.destroySingleton(source.jpaBeanName() + "_txManager");
        source.setJpaBeans(null);
        beans.entityManagerFactory().close();
    }
}
