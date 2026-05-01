package org.solarframework.db.v1;

import java.sql.Blob;
import java.sql.SQLException;

public class QueryParameter {
    public String type;
    public String fieldName;
    public Object value;

    public QueryParameter(String f, Object value) {
        this.type = DBUpdate.SET;
        this.fieldName = f;
        this.value = value;

        if (value instanceof Blob blob) {
            try {
                this.value = blob.getBytes(1, (int) blob.length());
            } catch (SQLException ignored) {}
        }
    }
    public QueryParameter(String f, Object value, String type) {
        this.type = type;
        this.fieldName = f;
        this.value = value;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getValue() {
        return value;
    }
}

