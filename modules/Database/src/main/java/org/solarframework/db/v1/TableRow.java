package org.solarframework.db.v1;

import java.lang.reflect.Field;
import java.sql.Date;
import java.util.List;

import static org.solarframework.db.v1.DatabaseManager.*;

public class TableRow {
    public transient List<QueryParameter> QP;

    public TableRow(List<QueryParameter> qp) {
        this.QP = qp;
    }

    public <T> T get(Class<T> clazz, String fieldName) {
        for (QueryParameter qp : QP) {
            if (qp.fieldName.equals(fieldName) && qp.value.getClass().equals(clazz)) {
                return clazz.cast(qp.value);
            }
        }
        return null;
    }
    public Object get(String fieldName) {
        for (QueryParameter qp : QP) {
            if (qp.fieldName.equals(fieldName)) {
                return qp.value;
            }
        }
        return null;
    }
    public String getAsString(String fieldName) {
        for (QueryParameter qp : QP) {
            if (qp.fieldName.equals(fieldName)) {
                if (qp.value == null) return null;
                return qp.value + "";
            }
        }
        return null;
    }
    public int getAsInt(String fieldName) {
        try {
            return Integer.parseInt(getAsString(fieldName));
        } catch (Exception ignored) {
            return 0;
        }
    }
    public long getAsLong(String fieldName) {
        try {
            return Long.parseLong(getAsString(fieldName));
        } catch (Exception ignored) {
            return 0;
        }
    }
    public double getAsDouble(String fieldName) {
        try {
            return Double.parseDouble(getAsString(fieldName));
        } catch (Exception ignored) {
            return 0;
        }
    }
    public short getAsShort(String fieldName) {
        try {
            return Short.parseShort(getAsString(fieldName));
        } catch (Exception ignored) {
            return 0;
        }
    }
    public float getAsFloat(String fieldName) {
        try {
            return Float.parseFloat(getAsString(fieldName));
        } catch (Exception ignored) {
            return 0;
        }
    }
    public boolean getAsBoolean(String fieldName) {
        try {
            return Boolean.parseBoolean(getAsString(fieldName));
        } catch (Exception ignored) {
            return false;
        }
    }
    public byte getAsByte(String fieldName) {
        try {
            return Byte.parseByte(getAsString(fieldName));
        } catch (Exception ignored) {
            return 0;
        }
    }


    public <T> T mapTo(Class<T> clazz){
        try {
            T Item = ConstructItem(clazz);
            List<Field> Fields = getFields(Item, true);
            for (int i = 1; i <= QP.size(); i++) {
                try {
                    Field F = getFieldIgnoreCase(Fields, QP.get(i-1).getFieldName());
                    if (QP.get(i-1).getValue() instanceof Date D) {
                        F.set(Item, D.toLocalDate());
                    } else {
                        F.set(Item, QP.get(i - 1).getValue());
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
            return Item;
        } catch (Exception ignored) {
            return null;
        }
    }

}
