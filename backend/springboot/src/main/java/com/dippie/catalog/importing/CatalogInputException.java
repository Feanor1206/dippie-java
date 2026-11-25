package com.dippie.catalog.importing;

import com.dippie.common.error.InvalidRequestException;

public class CatalogInputException extends InvalidRequestException {

    private final String path;

    public CatalogInputException(String path, String message) {
        super(message);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
