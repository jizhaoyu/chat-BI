package com.jizhaoyu.chatbi.application.sqlguard;

import com.jizhaoyu.chatbi.domain.catalog.CatalogSnapshot;

import java.util.Objects;

public record SqlGuardContext(CatalogSnapshot authorizedCatalog, int maximumRows) {
    public SqlGuardContext {
        Objects.requireNonNull(authorizedCatalog, "authorizedCatalog");
        if (maximumRows < 1) {
            throw new IllegalArgumentException("SQL_MAXIMUM_ROWS_INVALID");
        }
    }
}
