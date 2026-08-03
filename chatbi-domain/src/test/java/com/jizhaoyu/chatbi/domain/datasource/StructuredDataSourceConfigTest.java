package com.jizhaoyu.chatbi.domain.datasource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredDataSourceConfigTest {
    @ParameterizedTest
    @MethodSource("malformedOrAmbiguousHosts")
    void rejectsMalformedOrAmbiguousHostValues(String host) {
        assertThatThrownBy(() -> config(host))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATASOURCE_HOST_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0",
            "127.0.0.0",
            "127.0.0.1",
            "127.255.255.255",
            "169.254.0.0",
            "169.254.255.255",
            "10.0.0.0",
            "10.255.255.255",
            "172.16.0.0",
            "172.31.255.255",
            "192.168.0.0",
            "192.168.1.10",
            "192.168.255.255",
            "100.64.0.0",
            "100.127.255.255",
            "224.0.0.0",
            "239.255.255.255"
    })
    void rejectsNonPublicIpv4Destinations(String host) {
        assertThatThrownBy(() -> config(host))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATASOURCE_HOST_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::",
            "::1",
            "fe80::1",
            "febf::1",
            "fec0::1",
            "feff::1",
            "fc00::1",
            "fdff::1",
            "ff02::1",
            "::ffff:8.8.8.8",
            "0:0:0:0:0:ffff:808:808"
    })
    void rejectsNonPublicAndIpv4MappedIpv6Destinations(String host) {
        assertThatThrownBy(() -> config(host))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DATASOURCE_HOST_NOT_ALLOWED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "db-01.analytics.example",
            "EXAMPLE.COM",
            "8.8.8.8",
            "100.63.255.255",
            "100.128.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "2001:4860:4860::8888",
            "2606:4700:4700::1111"
    })
    void acceptsStrictPublicDomainAndIpHosts(String host) {
        assertThat(config(host).host()).isEqualTo(host);
    }

    private static Stream<String> malformedOrAmbiguousHosts() {
        return Stream.of(
                "jdbc:mysql://internal",
                "http://example.com",
                "reader@example.com",
                "example.com.",
                "2130706433",
                "127.1",
                "127.0.1",
                "0177.0.0.1",
                "127.0.0.01",
                "0x7f000001",
                "0x7f.0.0.1",
                "127.0.0.0x1",
                "1.2.3.999",
                "-db.example.com",
                "db-.example.com",
                "db..example.com",
                "db_name.example.com",
                "a".repeat(64) + ".example.com");
    }

    private static StructuredDataSourceConfig config(String host) {
        return new StructuredDataSourceConfig(
                host, 3306, "sales", "reader", "secret/ref", DataSourceDialect.MYSQL, 1000, 30);
    }
}
