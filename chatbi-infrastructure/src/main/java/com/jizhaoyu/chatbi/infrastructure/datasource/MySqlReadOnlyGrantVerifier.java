package com.jizhaoyu.chatbi.infrastructure.datasource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class MySqlReadOnlyGrantVerifier {
    void verify(Connection connection, String database) throws SQLException {
        boolean sawGrant = false;
        try (Statement statement = connection.createStatement();
             ResultSet grants = statement.executeQuery("SHOW GRANTS")) {
            while (grants.next()) {
                sawGrant = true;
                if (!isStrictReadOnlyGrant(grants.getString(1))
                        || !hasAllowedScope(grants.getString(1), database)) {
                    throw new ReadOnlyGrantRequiredException();
                }
            }
        }
        if (!sawGrant) throw new ReadOnlyGrantRequiredException();
    }

    static boolean isStrictReadOnlyGrant(String grant) {
        if (grant == null) return false;
        String normalized = grant.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("GRANT ") || normalized.contains(" WITH GRANT OPTION")) return false;
        int onIndex = normalized.indexOf(" ON ", 6);
        if (onIndex < 0) return false;
        List<String> privileges = splitTopLevel(normalized.substring(6, onIndex).trim());
        return !privileges.isEmpty() && privileges.stream().allMatch(MySqlReadOnlyGrantVerifier::isReadOnlyPrivilege);
    }

    static boolean hasAllowedScope(String grant, String database) {
        if (grant == null || database == null || database.isBlank()) return false;
        String normalized = grant.trim().toUpperCase(Locale.ROOT).replace("`", "");
        int onIndex = normalized.indexOf(" ON ", 6);
        int toIndex = normalized.indexOf(" TO ", onIndex + 4);
        if (onIndex < 0 || toIndex < 0) return false;
        List<String> privileges = splitTopLevel(normalized.substring(6, onIndex).trim());
        String scope = normalized.substring(onIndex + 4, toIndex).trim();
        if (privileges.stream().allMatch("USAGE"::equals)) {
            return "*.*".equals(scope);
        }
        String expectedPrefix = database.toUpperCase(Locale.ROOT) + ".";
        return privileges.stream().allMatch(MySqlReadOnlyGrantVerifier::isSelectPrivilege)
                && scope.startsWith(expectedPrefix)
                && scope.length() > expectedPrefix.length();
    }

    private static List<String> splitTopLevel(String clause) {
        List<String> privileges = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < clause.length(); index++) {
            char character = clause.charAt(index);
            if (character == '(') depth++;
            else if (character == ')') depth--;
            else if (character == ',' && depth == 0) {
                privileges.add(clause.substring(start, index).trim());
                start = index + 1;
            }
            if (depth < 0) return List.of();
        }
        if (depth != 0) return List.of();
        privileges.add(clause.substring(start).trim());
        return privileges;
    }

    private static boolean isReadOnlyPrivilege(String privilege) {
        if (privilege.equals("SELECT") || privilege.equals("USAGE")) return true;
        if (!privilege.startsWith("SELECT (")) return false;
        int closingParenthesis = privilege.lastIndexOf(')');
        return closingParenthesis == privilege.length() - 1 && closingParenthesis > "SELECT (".length();
    }

    private static boolean isSelectPrivilege(String privilege) {
        return privilege.equals("SELECT") || privilege.startsWith("SELECT (");
    }

    static final class ReadOnlyGrantRequiredException extends SQLException {
        ReadOnlyGrantRequiredException() {
            super("DATASOURCE_ACCOUNT_NOT_READ_ONLY");
        }
    }
}
