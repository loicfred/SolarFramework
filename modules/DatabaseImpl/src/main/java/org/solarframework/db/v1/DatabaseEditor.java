package org.solarframework.db.v1;

import org.solarframework.db.v1.manipulator.ItemCreator;

import java.util.ArrayList;
import java.util.List;

public class DatabaseEditor <I> {
    private final transient DatabaseManager DBM;
    private transient List<QueryParameter> Set = new ArrayList<>();
    private final transient Class<I> clazz;
    private transient I obj;

    public DatabaseEditor(DatabaseManager DBM, I obj, Class<I> clazz) {
        this.DBM = DBM;
        this.obj = obj;
        this.clazz = clazz;
    }

    public <M> M AddSet(String f, M newvalue) {
        Set.removeIf(M -> M.fieldName.equalsIgnoreCase(f));
        Set.add(new QueryParameter(f, newvalue));
        return newvalue;
    }
    public <M> void AddIncrement(String f, M addValue) {
        Set.removeIf(M -> M.fieldName.equalsIgnoreCase(f));
        Set.add(new QueryParameter(f, addValue, DBUpdate.INCREMENT));
    }

    public int Update(String where, Object... o) {
        int i = 0;
        if (!Set.isEmpty()) i = DBM.updateItems(clazz).set(new ArrayList<>(Set)).where(where, o).updateFirst();
        Set = new ArrayList<>();
        return i;
    }
    public I UpdateSet(String where, Object... o) {
        if (!Set.isEmpty()) obj = DBM.updateItems(clazz).set(new ArrayList<>(Set)).where(where, o).returning().get(0).mapTo(clazz);
        Set = new ArrayList<>();
        return obj;
    }
    public I Write(boolean returning, boolean update) {
        ItemCreator C = DBM.createItem(clazz);
        if (update) C.updateIfExist();
        if (returning) return obj = C.returning(obj).mapTo(clazz);
        C.create(obj);
        return obj;
    }
    public I WriteSet(boolean returning, boolean update) {
        ItemCreator C = DBM.createItem(clazz);
        if (update) C.updateIfExist();
        if (returning) return C.returning(obj, Set).mapTo(clazz);
        C.create(obj);
        return obj;
    }

    public int Delete(String where, Object... o) {
        Set = new ArrayList<>();
        return DBM.deleteItems(clazz).where(where, o).deleteFirst();
    }

}
