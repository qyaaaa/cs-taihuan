package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.json.Json;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Config {
    public static final String DEFAULT_BUFF_BASE_URL = "https://buff.163.com";

    private Config() {
    }

    public static String loadCookie(String explicitCookie) {
        String cookie = explicitCookie == null || explicitCookie.trim().isEmpty()
            ? System.getenv("BUFF_COOKIE")
            : explicitCookie;
        if (cookie == null || cookie.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing BUFF cookie. Pass --cookie or set BUFF_COOKIE.");
        }
        return cookie.trim();
    }

    public static List<CatalogSkin> loadCatalog(Path path) throws IOException {
        Object root = Json.parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        if (!(root instanceof List)) {
            throw new IllegalArgumentException("Catalog must be a JSON array.");
        }
        List<?> rows = (List<?>) root;
        List<CatalogSkin> skins = new ArrayList<CatalogSkin>();
        for (Object row : rows) {
            if (!(row instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) row;
            skins.add(CatalogSkin.fromMap(payload));
        }
        return skins;
    }
}

