package com.brewpos;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Enables PostgreSQL integration tests whenever a real DB_PASSWORD is supplied
 * via OS environment variable, JVM property, or a local .env file.
 */
public final class DbTestCondition {

    private static final String PLACEHOLDER = "your_password_here";

    private DbTestCondition() {
    }

    public static boolean isDbConfigured() {
        if (isRealValue(System.getenv("DB_PASSWORD")) || isRealValue(System.getProperty("DB_PASSWORD"))) {
            return true;
        }
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return false;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
            props.load(reader);
            return isRealValue(props.getProperty("DB_PASSWORD"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isRealValue(String value) {
        return value != null && !value.isBlank() && !PLACEHOLDER.equals(value.trim());
    }
}

