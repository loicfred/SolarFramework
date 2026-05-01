package org.solarframework.db.v1;

import java.util.ArrayList;
import java.util.List;

public class QueryBuilder {
    String query = "";
    List<Object> QP = new ArrayList<>();


    public QueryBuilder() {}
    public QueryBuilder(String query) {
        this.query = query;
    }

    public QueryBuilder add(String moreQuery) {
        this.query = this.query + moreQuery;
        return this;
    }

    public QueryBuilder addParam(Object param) {
        QP.add(param);
        return this;
    }

}
