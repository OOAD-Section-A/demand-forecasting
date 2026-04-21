package com.forecast.diagnostics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public final class DatabaseBootstrapDiagnostic {

    private DatabaseBootstrapDiagnostic() {
    }

    public static void main(String[] args) throws SQLException {
        String configuredUrl = firstNonBlank(
            System.getProperty("db.url"),
            System.getenv("DB_URL"),
            "jdbc:mysql://localhost:3306/OOAD"
        );
        String username = firstNonBlank(
            System.getProperty("db.username"),
            System.getenv("DB_USERNAME"),
            "CHANGE_ME"
        );
        String password = firstNonNull(
            System.getProperty("db.password"),
            System.getenv("DB_PASSWORD"),
            "CHANGE_ME"
        );

        String schema = schemaName(configuredUrl);
        String serverUrl = serverUrl(configuredUrl);

        System.out.println("Configured URL : " + configuredUrl);
        System.out.println("Server URL     : " + serverUrl);
        System.out.println("Schema         : " + schema);
        System.out.println("Username       : " + username);

        try (Connection serverConnection = DriverManager.getConnection(serverUrl, username, password);
             Statement statement = serverConnection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS `" + schema.replace("`", "``") + "`");
            System.out.println("CREATE DATABASE check succeeded.");
        }

        try (Connection schemaConnection = DriverManager.getConnection(configuredUrl, username, password);
             ResultSet resultSet = schemaConnection.getMetaData().getTables(schema, null, "%", null)) {
            int tableCount = 0;
            while (resultSet.next()) {
                tableCount++;
            }
            System.out.println("Schema connection succeeded. Existing table count=" + tableCount);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String schemaName(String url) {
        String body = url.substring("jdbc:mysql://".length());
        int slash = body.indexOf('/');
        if (slash < 0 || slash == body.length() - 1) {
            throw new IllegalArgumentException("URL must include schema: " + url);
        }
        String schema = body.substring(slash + 1);
        int query = schema.indexOf('?');
        if (query >= 0) {
            schema = schema.substring(0, query);
        }
        return schema;
    }

    private static String serverUrl(String url) {
        String prefix = "jdbc:mysql://";
        if (!url.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            return url;
        }
        String body = url.substring(prefix.length());
        int slash = body.indexOf('/');
        if (slash < 0) {
            return url;
        }
        String hostPort = body.substring(0, slash);
        String schemaAndQuery = body.substring(slash + 1);
        int query = schemaAndQuery.indexOf('?');
        String queryString = query >= 0 ? schemaAndQuery.substring(query) : "";
        return prefix + hostPort + queryString;
    }
}
