package org.solarframework.db.api;

import org.solarframework.db.spring.DatabaseObject;

import java.util.List;
import java.util.function.Supplier;

public interface BundleEntities {

    Supplier<List<? extends DatabaseObject<?>>> bundleEntities();
}
