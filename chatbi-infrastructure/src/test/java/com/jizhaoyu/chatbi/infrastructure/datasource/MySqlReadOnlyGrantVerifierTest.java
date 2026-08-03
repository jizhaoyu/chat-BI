package com.jizhaoyu.chatbi.infrastructure.datasource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlReadOnlyGrantVerifierTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "GRANT USAGE ON *.* TO 'reader'@'%'",
            "GRANT SELECT ON `sample_sales`.* TO 'reader'@'%'",
            "grant select (id, amount) on sample_sales.fact_order to 'reader'@'%'"
    })
    void acceptsStrictReadOnlyGrants(String grant) {
        assertThat(MySqlReadOnlyGrantVerifier.isStrictReadOnlyGrant(grant)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GRANT ALL PRIVILEGES ON *.* TO 'reader'@'%'",
            "GRANT SELECT, INSERT ON sample_sales.* TO 'reader'@'%'",
            "GRANT UPDATE ON sample_sales.* TO 'reader'@'%'",
            "GRANT SELECT ON sample_sales.* TO 'reader'@'%' WITH GRANT OPTION",
            "GRANT 'reporting_role'@'%' TO 'reader'@'%'",
            "SELECT"
    })
    void rejectsWritableOrAmbiguousGrants(String grant) {
        assertThat(MySqlReadOnlyGrantVerifier.isStrictReadOnlyGrant(grant)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GRANT USAGE ON *.* TO 'reader'@'%'",
            "GRANT SELECT ON `sample_sales`.* TO 'reader'@'%'",
            "GRANT SELECT (id) ON sample_sales.fact_order TO 'reader'@'%'"
    })
    void acceptsOnlyUsageOrSelectInsideConfiguredDatabase(String grant) {
        assertThat(MySqlReadOnlyGrantVerifier.hasAllowedScope(grant, "sample_sales")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GRANT SELECT ON *.* TO 'reader'@'%'",
            "GRANT SELECT ON other_database.* TO 'reader'@'%'",
            "GRANT USAGE ON sample_sales.* TO 'reader'@'%'"
    })
    void rejectsReadPrivilegesOutsideConfiguredDatabase(String grant) {
        assertThat(MySqlReadOnlyGrantVerifier.hasAllowedScope(grant, "sample_sales")).isFalse();
    }
}
