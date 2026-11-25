package com.dippie.assistant.search;

import java.util.List;

public interface CatalogSearch {

    boolean isReady();

    default int documentCount() {
        return 0;
    }

    List<SearchHit> search(String query, int limit, SearchConstraints constraints);
}
