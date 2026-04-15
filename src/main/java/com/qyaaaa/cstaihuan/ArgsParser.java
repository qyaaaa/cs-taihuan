package com.qyaaaa.cstaihuan;

import java.util.HashMap;
import java.util.Map;

public final class ArgsParser {
    private final Map<String, String> values = new HashMap<String, String>();

    public ArgsParser(String[] args) {
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                continue;
            }
            String key = token.substring(2);
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            values.put(key, value);
        }
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required argument --" + key);
        }
        return value;
    }

    public String get(String key, String defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    public Integer getNullableInt(String key) {
        String value = values.get(key);
        return value == null ? null : Integer.valueOf(value);
    }

    public double getDouble(String key, double defaultValue) {
        String value = values.get(key);
        return value == null ? defaultValue : Double.parseDouble(value);
    }
}

