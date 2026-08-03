package com.jizhaoyu.chatbi.infrastructure.datasource;

import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionSpec;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

final class MySqlJdbcConnections {
    private MySqlJdbcConnections() {
    }

    static String jdbcUrl(InetAddress address, ExternalDataSourceConnectionSpec spec) {
        String literal = address.getHostAddress();
        int zoneIndex = literal.indexOf('%');
        if (zoneIndex >= 0) literal = literal.substring(0, zoneIndex);
        if (address instanceof Inet6Address) literal = '[' + literal + ']';
        return "jdbc:mysql://" + literal + ':' + spec.port() + '/' + spec.database();
    }

    static Properties connectionProperties(ExternalDataSourceConnectionSpec spec) {
        Properties properties = new Properties();
        properties.setProperty("user", spec.username());
        properties.setProperty("password", spec.password());
        properties.setProperty("connectTimeout", Integer.toString(spec.connectionTimeoutSeconds() * 1_000));
        properties.setProperty("socketTimeout", Integer.toString(spec.connectionTimeoutSeconds() * 1_000));
        properties.setProperty("allowMultiQueries", "false");
        properties.setProperty("readOnlyPropagatesToServer", "true");
        return properties;
    }

    static Connection connect(String jdbcUrl, Properties properties) throws SQLException {
        return DriverManager.getConnection(jdbcUrl, properties);
    }
}
