package com.jizhaoyu.chatbi.application.datasource;

import com.jizhaoyu.chatbi.domain.datasource.DataSourceDialect;

public final class DataSourceCommand {
    private final String name;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final DataSourceDialect dialect;
    private final int maxRows;
    private final int timeoutSeconds;

    public DataSourceCommand(String name, String host, int port, String database, String username, String password,
                             DataSourceDialect dialect, int maxRows, int timeoutSeconds) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.dialect = dialect;
        this.maxRows = maxRows;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String name() { return name; }
    public String host() { return host; }
    public int port() { return port; }
    public String database() { return database; }
    public String username() { return username; }
    public String password() { return password; }
    public DataSourceDialect dialect() { return dialect; }
    public int maxRows() { return maxRows; }
    public int timeoutSeconds() { return timeoutSeconds; }

    @Override
    public String toString() {
        return "DataSourceCommand[name=" + name + ", host=" + host + ", port=" + port
                + ", database=" + database + ", username=" + username + ", password=[REDACTED], dialect="
                + dialect + ", maxRows=" + maxRows + ", timeoutSeconds=" + timeoutSeconds + ']';
    }
}
