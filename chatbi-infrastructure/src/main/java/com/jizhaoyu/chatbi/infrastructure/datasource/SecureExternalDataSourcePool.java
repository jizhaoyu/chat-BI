package com.jizhaoyu.chatbi.infrastructure.datasource;

import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionSpec;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourcePool;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class SecureExternalDataSourcePool implements ExternalDataSourcePool {
    private final SecureDnsResolver dnsResolver;
    private final ConcurrentHashMap<PoolKey, PoolEntry> pools = new ConcurrentHashMap<>();

    public SecureExternalDataSourcePool(SecureDnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    @Override
    public DataSource getOrCreate(ExternalDataSourceConnectionSpec connectionSpec) {
        PoolKey key = new PoolKey(connectionSpec.tenantId(), connectionSpec.dataSourceId());
        return pools.compute(key, (ignored, existing) -> {
            if (existing != null && existing.spec().equals(connectionSpec) && !existing.pool().isClosed()) {
                return existing;
            }
            close(existing);
            InetAddress pinnedAddress = dnsResolver.resolvePublicAddresses(connectionSpec.host()).getFirst();
            return new PoolEntry(connectionSpec, createPool(connectionSpec, pinnedAddress));
        }).pool();
    }

    @Override
    public void destroy(UUID tenantId, UUID dataSourceId) {
        close(pools.remove(new PoolKey(tenantId, dataSourceId)));
    }

    @Override
    public void close() {
        pools.values().forEach(SecureExternalDataSourcePool::close);
        pools.clear();
    }

    private static HikariDataSource createPool(
            ExternalDataSourceConnectionSpec spec, InetAddress pinnedAddress) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MySqlJdbcConnections.jdbcUrl(pinnedAddress, spec));
        config.setUsername(spec.username());
        config.setPassword(spec.password());
        config.setMaximumPoolSize(spec.maximumPoolSize());
        config.setMinimumIdle(0);
        config.setReadOnly(true);
        config.setAutoCommit(true);
        config.setConnectionTimeout(spec.connectionTimeoutSeconds() * 1_000L);
        config.setValidationTimeout(Math.min(5_000L, spec.connectionTimeoutSeconds() * 1_000L));
        config.setPoolName("chatbi-external-" + shortId(spec.tenantId()) + '-' + shortId(spec.dataSourceId()));
        config.addDataSourceProperty("allowMultiQueries", "false");
        config.addDataSourceProperty("allowLoadLocalInfile", "false");
        config.addDataSourceProperty("allowUrlInLocalInfile", "false");
        config.addDataSourceProperty("readOnlyPropagatesToServer", "true");
        config.addDataSourceProperty("connectTimeout", spec.connectionTimeoutSeconds() * 1_000);
        config.addDataSourceProperty("socketTimeout", spec.connectionTimeoutSeconds() * 1_000);
        return new HikariDataSource(config);
    }

    private static String shortId(UUID value) {
        return value.toString().substring(0, 8);
    }

    private static void close(PoolEntry entry) {
        if (entry != null) {
            entry.pool().close();
        }
    }

    private record PoolKey(UUID tenantId, UUID dataSourceId) {
        private PoolKey {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(dataSourceId, "dataSourceId");
        }
    }

    private record PoolEntry(ExternalDataSourceConnectionSpec spec, HikariDataSource pool) {
    }
}
