package com.jizhaoyu.chatbi.infrastructure.datasource;

public final class UnsafeDataSourceHostException extends RuntimeException {
    public UnsafeDataSourceHostException(String code) {
        super(code);
    }

    public UnsafeDataSourceHostException(String code, Throwable cause) {
        super(code, cause);
    }
}
