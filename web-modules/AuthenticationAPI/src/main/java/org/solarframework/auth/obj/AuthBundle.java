package org.solarframework.auth.obj;

import org.solarframework.db.api.BundleEntities;
import org.solarframework.db.spring.DatabaseObject;

import java.util.List;
import java.util.function.Supplier;

public class AuthBundle implements BundleEntities {
    @Override
    public Supplier<List<? extends DatabaseObject<?>>> bundleEntities() {
        return () -> List.of(
                new Account_Role(1L, "USER", 0),
                new Account_Role(2L, "MODERATOR", 50),
                new Account_Role(3L, "STAFF", 75),
                new Account_Role(4L, "ADMIN", 85),
                new Account_Role(5L, "CEO", 100)
        );
    }
}
