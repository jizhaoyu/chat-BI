package com.jizhaoyu.chatbi.application.sqlguard;

public interface SqlGuardPort {
    SqlGuardResult validate(String candidateSql, SqlGuardContext context);
}
