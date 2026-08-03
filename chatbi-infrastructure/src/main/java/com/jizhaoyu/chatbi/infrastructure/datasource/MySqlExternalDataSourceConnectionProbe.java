package com.jizhaoyu.chatbi.infrastructure.datasource;

import com.jizhaoyu.chatbi.application.datasource.ConnectionProbeResult;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionProbe;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourceConnectionSpec;
import com.jizhaoyu.chatbi.application.datasource.ExternalDataSourcePool;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public final class MySqlExternalDataSourceConnectionProbe implements ExternalDataSourceConnectionProbe {
    private final ExternalDataSourcePool pools;
    private final MySqlReadOnlyGrantVerifier grantVerifier = new MySqlReadOnlyGrantVerifier();

    public MySqlExternalDataSourceConnectionProbe(ExternalDataSourcePool pools) {
        this.pools = pools;
    }

    @Override
    public ConnectionProbeResult probe(ExternalDataSourceConnectionSpec spec) {
        try (Connection connection = pools.getOrCreate(spec).getConnection()) {
            connection.setReadOnly(true);
            grantVerifier.verify(connection, spec.database());
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(spec.connectionTimeoutSeconds());
                try (ResultSet result = statement.executeQuery("SELECT 1")) {
                    if (!result.next() || result.getInt(1) != 1) {
                        return failure(spec, "DATASOURCE_UNAVAILABLE", "数据源未返回有效探测结果");
                    }
                }
            }
            return ConnectionProbeResult.success();
        } catch (UnsafeDataSourceHostException exception) {
            return failure(spec, exception.getMessage(), "数据源主机地址不允许");
        } catch (MySqlReadOnlyGrantVerifier.ReadOnlyGrantRequiredException exception) {
            return failure(spec, "DATASOURCE_NOT_READ_ONLY", "数据源账号不是严格只读账号");
        } catch (SQLException | RuntimeException exception) {
            return failure(spec, "DATASOURCE_UNAVAILABLE", "数据源连接失败");
        }
    }

    private ConnectionProbeResult failure(
            ExternalDataSourceConnectionSpec spec, String code, String message) {
        pools.destroy(spec.tenantId(), spec.dataSourceId());
        return ConnectionProbeResult.failure(code, message);
    }
}
