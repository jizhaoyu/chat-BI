package com.jizhaoyu.chatbi.domain.datasource;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.regex.Pattern;

public record StructuredDataSourceConfig(
        String host,
        int port,
        String database,
        String username,
        String credentialRef,
        DataSourceDialect dialect,
        int maxRows,
        int timeoutSeconds) {

    private static final Pattern DOMAIN_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");
    private static final Pattern IPV4_CANDIDATE = Pattern.compile("[0-9.]+");
    private static final Pattern NONSTANDARD_IPV4_CANDIDATE = Pattern.compile(
            "(?i)(?:0x[0-9a-f]+|[0-9]+)(?:\\.(?:0x[0-9a-f]+|[0-9]+))*");
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9A-Fa-f:.]+");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]{1,64}");
    private static final Pattern CREDENTIAL_REF = Pattern.compile("[A-Za-z0-9._:/-]{1,128}");

    public StructuredDataSourceConfig {
        host = require(host, "host");
        database = require(database, "database");
        username = require(username, "username");
        credentialRef = require(credentialRef, "credentialRef");
        Objects.requireNonNull(dialect, "dialect");
        if (!isAllowedHost(host)) {
            throw new IllegalArgumentException("DATASOURCE_HOST_NOT_ALLOWED");
        }
        if (!DATABASE.matcher(database).matches()) {
            throw new IllegalArgumentException("DATASOURCE_DATABASE_NOT_ALLOWED");
        }
        if (!CREDENTIAL_REF.matcher(credentialRef).matches()) {
            throw new IllegalArgumentException("DATASOURCE_CREDENTIAL_REF_NOT_ALLOWED");
        }
        if (port < 1 || port > 65535 || maxRows < 1 || maxRows > 1_000_000 || timeoutSeconds < 1 || timeoutSeconds > 600) {
            throw new IllegalArgumentException("DATASOURCE_RESOURCE_POLICY_NOT_ALLOWED");
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DATASOURCE_" + field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    private static boolean isAllowedHost(String host) {
        if (host.length() > 253 || host.endsWith(".") || host.equalsIgnoreCase("localhost")) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            return isAllowedIpv6Literal(host);
        }
        if (IPV4_CANDIDATE.matcher(host).matches()) {
            return isAllowedIpv4Literal(host);
        }
        if (NONSTANDARD_IPV4_CANDIDATE.matcher(host).matches()) {
            return false;
        }
        return isStrictDomainName(host);
    }

    private static boolean isStrictDomainName(String host) {
        String[] labels = host.split("\\.", -1);
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllowedIpv4Literal(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }

        byte[] address = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3 || octet.length() > 1 && octet.charAt(0) == '0') {
                return false;
            }
            int value = Integer.parseInt(octet);
            if (value > 255) {
                return false;
            }
            address[index] = (byte) value;
        }
        return isAllowedAddress(address);
    }

    private static boolean isAllowedIpv6Literal(String host) {
        if (!IPV6_LITERAL.matcher(host).matches()) {
            return false;
        }
        try {
            // The character gate ensures this call only parses a literal and never performs DNS lookup.
            InetAddress address = InetAddress.getByName(host);
            return address instanceof Inet6Address
                    && !isIpv4Mapped(address.getAddress())
                    && isAllowedAddress(address);
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static boolean isAllowedAddress(byte[] address) {
        try {
            return isAllowedAddress(InetAddress.getByAddress(address));
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static boolean isAllowedAddress(InetAddress address) {
        return !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isSiteLocalAddress()
                && !address.isMulticastAddress()
                && !isCarrierGradeNat(address)
                && !isUniqueLocalIpv6(address);
    }

    private static boolean isCarrierGradeNat(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isIpv4Mapped(byte[] address) {
        if (address.length != 16) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (address[index] != 0) {
                return false;
            }
        }
        return address[10] == (byte) 0xff && address[11] == (byte) 0xff;
    }
}
