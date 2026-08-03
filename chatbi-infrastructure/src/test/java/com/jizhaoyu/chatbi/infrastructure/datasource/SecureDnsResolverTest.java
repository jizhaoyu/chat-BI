package com.jizhaoyu.chatbi.infrastructure.datasource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureDnsResolverTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "10.0.0.1", "100.64.0.1", "127.0.0.1", "169.254.1.1",
            "172.16.0.1", "192.168.1.1", "224.0.0.1", "::", "::1", "fc00::1", "fe80::1",
            "::ffff:10.0.0.1"
    })
    void rejectsNonPublicAddress(String literal) throws Exception {
        SecureDnsResolver resolver = new SecureDnsResolver(
                ignored -> new InetAddress[]{InetAddress.getByName(literal)});

        assertThatThrownBy(() -> resolver.resolvePublicAddresses("analytics.example.com"))
                .isInstanceOf(UnsafeDataSourceHostException.class)
                .hasMessage("DATASOURCE_HOST_ADDRESS_NOT_ALLOWED");
    }

    @Test
    void rejectsHostWhenAnyResolvedAddressIsPrivate() throws Exception {
        SecureDnsResolver resolver = new SecureDnsResolver(ignored -> new InetAddress[]{
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.8")
        });

        assertThatThrownBy(() -> resolver.resolvePublicAddresses("rebinding.example.com"))
                .isInstanceOf(UnsafeDataSourceHostException.class)
                .hasMessage("DATASOURCE_HOST_ADDRESS_NOT_ALLOWED");
    }

    @Test
    void returnsOnlyVerifiedPublicAddresses() throws Exception {
        SecureDnsResolver resolver = new SecureDnsResolver(ignored -> new InetAddress[]{
                InetAddress.getByName("8.8.8.8"), InetAddress.getByName("2606:4700:4700::1111")
        });

        assertThat(resolver.resolvePublicAddresses("analytics.example.com"))
                .extracting(InetAddress::getHostAddress)
                .containsExactly("8.8.8.8", "2606:4700:4700:0:0:0:0:1111");
    }
}
