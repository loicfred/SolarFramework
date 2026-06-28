package org.solarframework.db.v1;

import java.sql.SQLException;

public interface DBSaver<T> {

    T Write() throws SQLException;
    int Update() throws SQLException;
    int Delete() throws SQLException;
}
