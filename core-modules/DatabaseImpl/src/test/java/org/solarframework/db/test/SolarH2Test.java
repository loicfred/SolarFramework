package org.solarframework.db.test;

import org.solarframework.db.spring.SolarRegistryListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * One Spring context for every H2-backed test class in this module. Spring caches a context per distinct
 * configuration, so the properties have to be spelled once, here, rather than copied per class: a single
 * character of drift - a different {@code mem:} name most of all - buys a whole extra Spring Boot startup.
 * Sharing the schema is safe because every class wipes the tables it uses in its own {@code @BeforeEach}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = Database_Main.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:solartest;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER",
        "spring.datasource.username=sa",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=none"
})
@TestExecutionListeners(value = SolarRegistryListener.class, mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public @interface SolarH2Test {}
