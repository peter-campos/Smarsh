package com.smarsh.urlcache.exception;

public class CacheFetchException extends Exception {

    public CacheFetchException(String message) {
        super(message);
    }

    public CacheFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
