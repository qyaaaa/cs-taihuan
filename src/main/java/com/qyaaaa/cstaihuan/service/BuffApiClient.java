package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BuffApiClient {
    private final RestTemplate restTemplate;

    public BuffApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<BuffItem> fetchInventory(String baseUrl, String cookie, String game, int pageSize, Integer maxPages) {
        List<BuffItem> items = new ArrayList<BuffItem>();
        int page = 1;
        while (true) {
            Map<String, Object> payload = request(baseUrl, cookie, game, page, pageSize);
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

    private Map<String, Object> request(String baseUrl, String cookie, String game, int page, int pageSize) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/market/steam_inventory")
            .queryParam("game", game)
            .queryParam("page_num", page)
            .queryParam("page_size", pageSize)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 SpringBoot cs-taihuan");
        headers.set(HttpHeaders.REFERER, baseUrl + "/market/csgo");
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.setAccept(MediaType.parseMediaTypes("application/json, text/plain, */*"));

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Object>(headers), Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("BUFF API request failed: " + response.getStatusCode());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = response.getBody();
        return payload;
    }

    private List<BuffItem> extractItems(Map<String, Object> payload) {
        Object dataObj = payload.containsKey("data") ? payload.get("data") : payload;
        if (!(dataObj instanceof Map)) {
            return new ArrayList<BuffItem>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object[] sources = new Object[] {data.get("items"), data.get("inventory"), data.get("goods_infos"), payload.get("items")};
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

