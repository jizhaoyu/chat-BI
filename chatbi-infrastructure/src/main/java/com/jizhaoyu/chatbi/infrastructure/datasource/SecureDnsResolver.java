package com.jizhaoyu.chatbi.infrastructure.datasource;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

@Component
public final class SecureDnsResolver {
    private final DnsLookup dnsLookup;

    public SecureDnsResolver() {
        this(InetAddress::getAllByName);
    }

    SecureDnsResolver(DnsLookup dnsLookup) {
        this.dnsLookup = dnsLookup;
    }

    public List<InetAddress> resolvePublicAddresses(String host) {
        InetAddress[] resolved;
        try {
            resolved = dnsLookup.resolve(host);
        } catch (UnknownHostException exception) {
            throw new UnsafeDataSourceHostException("DATASOURCE_HOST_UNRESOLVED", exception);
        }
        if (resolved.length == 0) {
            throw new UnsafeDataSourceHostException("DATASOURCE_HOST_UNRESOLVED");
        }
        if (Arrays.stream(resolved).anyMatch(SecureDnsResolver::isDisallowed)) {
            throw new UnsafeDataSourceHostException("DATASOURCE_HOST_ADDRESS_NOT_ALLOWED");
        }
        return List.copyOf(Arrays.asList(resolved));
    }

    static boolean isDisallowed(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNat(address)
                || isUniqueLocalIpv6(address)
                || isMappedPrivateIpv4(bytes);
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) return false;
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isMappedPrivateIpv4(byte[] bytes) {
        if (!isIpv4Mapped(bytes)) return false;
        int first = Byte.toUnsignedInt(bytes[12]);
        int second = Byte.toUnsignedInt(bytes[13]);
        return first == 10 || first == 127 || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 168 || first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) return false;
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) return false;
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    @FunctionalInterface
    interface DnsLookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
