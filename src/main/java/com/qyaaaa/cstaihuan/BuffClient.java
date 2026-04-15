package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.json.Json;
import com.qyaaaa.cstaihuan.model.BuffItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BuffClient {
    private final String cookie;
    private final String baseUrl;
    private final int timeoutMillis;

    public BuffClient(String cookie, String baseUrl, int timeoutMillis) {
        this.cookie = cookie;
        this.baseUrl = baseUrl;
        this.timeoutMillis = timeoutMillis;
    }

    public List<BuffItem> fetchInventory(String game, int pageSize, Integer maxPages) throws IOException {
        List<BuffItem> items = new ArrayList<BuffItem>();
        int page = 1;
        while (true) {
            Map<String, Object> payload = get("/api/market/steam_inventory", game, page, pageSize);
            List<BuffItem> pageItems = extractItems(payload);
            if (pageItems.isEmpty()) {
                break;
            }
            items.addAll(pageItems);
            if (maxPages != null && page >= maxPages.intValue()) {
                break;
            }
            if (!hasMore(payload, page, pageSize)) {
                break;
            }
            page++;
        }
        return items;
    }

    private Map<String, Object> get(String path, String game, int page, int pageSize) throws IOException {
        String query = "game=" + encode(game) + "&page_num=" + page + "&page_size=" + pageSize;
        URL url = new URL(baseUrl + path + "?" + query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(timeoutMillis);
        connection.setReadTimeout(timeoutMillis);
        connection.setRequestProperty("Cookie", cookie);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 Java8 cs-taihuan");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Referer", baseUrl + "/market/csgo");
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
            ? connection.getInputStream()
            : connection.getErrorStream();
        String body = readAll(stream);
        if (status < 200 || status >= 300) {
            throw new IOException("BUFF API request failed: HTTP " + status + " body=" + body);
        }

        Object parsed = Json.parse(body);
        if (!(parsed instanceof Map)) {
            throw new IOException("Unexpected BUFF response: " + body);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) parsed;
        Object code = payload.get("code");
        if (code != null && !"OK".equals(String.valueOf(code)) && payload.get("data") == null) {
            throw new IOException("BUFF API returned unexpected payload: " + body);
        }
        return payload;
    }

    private List<BuffItem> extractItems(Map<String, Object> payload) {
        Object dataObj = payload.containsKey("data") ? payload.get("data") : payload;
        if (!(dataObj instanceof Map)) {
            return new ArrayList<BuffItem>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;

        Object[] sources = new Object[] {
            data.get("items"),
            data.get("inventory"),
            data.get("goods_infos"),
            payload.get("items")
        };

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Object source : sources) {
            if (source instanceof List) {
                List<?> list = (List<?>) source;
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = (Map<String, Object>) item;
                        rows.add(row);
                    }
                }
                break;
            }
            if (source instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapSource = (Map<String, Object>) source;
                for (Object value : mapSource.values()) {
                    if (value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> row = (Map<String, Object>) value;
                        rows.add(row);
                    }
                }
                break;
            }
        }

        List<BuffItem> items = new ArrayList<BuffItem>();
        for (Map<String, Object> row : rows) {
            BuffItem item = parseItem(row);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private BuffItem parseItem(Map<String, Object> raw) {
        Map<String, Object> goods = mapValue(raw.get("goods_info"));
        Map<String, Object> asset = mapValue(raw.get("asset_info"));
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        merged.putAll(goods);
        merged.putAll(asset);
        merged.putAll(raw);

        String name = stringValue(merged, "market_hash_name", "name", "short_name");
        if (name == null || name.isEmpty()) {
            return null;
        }

        return new BuffItem(
            stringValue(merged, "assetid", "asset_id", "id", "goods_id", "name"),
            name,
            doubleValue(merged, 0.0d, "sell_min_price", "price", "quick_price", "steam_price"),
            nullableDoubleValue(merged, "paintwear", "float_value", "goods_float"),
            stringValue(merged, "collection"),
            normalizeRarity(stringValue(merged, "rarity", "quality")),
            !("cannot_trade".equals(String.valueOf(merged.get("state"))) || merged.get("trade_cooldown") != null),
            stringValue(merged, "goods_id"),
            raw
        );
    }

    private boolean hasMore(Map<String, Object> payload, int page, int pageSize) {
        Object dataObj = payload.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Integer pageNum = integerValue(data.get("page_num"));
            Integer totalPage = integerValue(data.get("total_page"));
            Integer totalCount = integerValue(data.get("total_count"));
            if (pageNum != null && totalPage != null) {
                return pageNum.intValue() < totalPage.intValue();
            }
            if (totalCount != null) {
                return page * pageSize < totalCount.intValue();
            }
        }
        return false;
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        return new LinkedHashMap<String, Object>();
    }

    private static String stringValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Double nullableDoubleValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null || String.valueOf(value).trim().isEmpty()) {
                continue;
            }
            return Double.valueOf(String.valueOf(value));
        }
        return null;
    }

    private static double doubleValue(Map<String, Object> payload, double defaultValue, String... keys) {
        Double value = nullableDoubleValue(payload, keys);
        return value == null ? defaultValue : value.doubleValue();
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static String normalizeRarity(String rarity) {
        if (rarity == null) {
            return null;
        }
        String lowered = rarity.trim().toLowerCase();
        if ("milspec".equals(lowered) || "mil-spec grade".equals(lowered)) {
            return "mil-spec";
        }
        if ("restricted grade".equals(lowered)) {
            return "restricted";
        }
        if ("classified grade".equals(lowered)) {
            return "classified";
        }
        if ("consumer grade".equals(lowered)) {
            return "consumer";
        }
        if ("industrial grade".equals(lowered)) {
            return "industrial";
        }
        if ("covert grade".equals(lowered)) {
            return "covert";
        }
        return lowered;
    }
}

