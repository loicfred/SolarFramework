package org.solarframework.search.spring;

import org.solarframework.search.ISearchService;
import org.solarframework.search.NoSearchService;

public class SearchRegistry {
    public static ISearchService SolarSearch = new NoSearchService();
}
