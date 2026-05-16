package org.solarframework.db.spring;


import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

public class HibernateNamingStrategy implements PhysicalNamingStrategy {

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        if (name == null) return null;
        return Identifier.toIdentifier(name.getText().toLowerCase());
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
        return logicalName;
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        return name;
    }

    @Override
    public Identifier toPhysicalTypeName(Identifier logicalName, JdbcEnvironment jdbcEnvironment) {
        return PhysicalNamingStrategy.super.toPhysicalTypeName(logicalName, jdbcEnvironment);
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment context) {
        return name;
    }

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment context) {
        return name;
    }
}