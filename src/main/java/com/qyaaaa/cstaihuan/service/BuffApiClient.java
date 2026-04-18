package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.model.BuffItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BuffApiClient {
    private static final long PAGE_REQUEST_INTERVAL_MILLIS = 5000L;
    private static final Logger log = LoggerFactory.getLogger(BuffApiClient.class);
    private final RestTemplate restTemplate;

    public BuffApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<BuffItem> fetchInventory(String baseUrl, String cookie, String game, int pageSize, Integer maxPages) {
        List<BuffItem> items = new ArrayList<BuffItem>();
        int page = 1;
        while (true) {
            log.info("Requesting BUFF inventory page, game={}, page={}, pageSize={}", game, Integer.valueOf(page), Integer.valueOf(pageSize));
            Map<String, Object> payload = request(baseUrl, cookie, game, page, pageSize);
            List<BuffItem> pageItems = extractItems(payload);
            log.info("BUFF inventory page loaded, game={}, page={}, itemCount={}", game, Integer.valueOf(page), Integer.valueOf(pageItems.size()));
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
            // BUFF 对连续翻页请求比较敏感，主动放慢分页节奏，降低触发限流的概率。
            sleepBeforeNextPage(page + 1);
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

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Object>(headers), Map.class);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("BUFF request rate limited, url={}", url);
            throw new BuffRateLimitException("BUFF 当前触发限流，请稍后再试。若数据库里已有库存快照，系统会优先回退到最近一次保存的数据。");
        } catch (HttpClientErrorException ex) {
            log.error("BUFF request failed, url={}, status={}", url, ex.getStatusCode());
            throw new IllegalStateException("BUFF API request failed: " + ex.getStatusCode());
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("BUFF request returned invalid response, url={}, status={}", url, response.getStatusCode());
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
        log.info("Parsed BUFF inventory payload, rowCount={}, parsedItemCount={}", Integer.valueOf(rows.size()), Integer.valueOf(items.size()));
        return items;
    }

    private BuffItem parseItem(Map<String, Object> raw) {
        Map<String, Object> goods = mapValue(raw.get("goods_info"));
        Map<String, Object> asset = mapValue(raw.get("asset_info"));
        Map<String, Object> assetInfo = mapValue(asset.get("info"));
        Map<String, Object> tags = mapValue(mergeFirst(raw.get("tags"), goods.get("tags"), asset.get("tags")));
        Map<String, Object> rarityTag = mapValue(tags.get("rarity"));
        Map<String, Object> categoryTag = mapValue(tags.get("category"));
        Map<String, Object> weaponTag = mapValue(tags.get("weapon"));
        Map<String, Object> exteriorTag = mapValue(tags.get("exterior"));
        Map<String, Object> itemsetTag = mapValue(tags.get("itemset"));
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        merged.putAll(goods);
        merged.putAll(asset);
        merged.putAll(assetInfo);
        merged.putAll(raw);

        String name = stringValue(merged, "name", "short_name", "market_hash_name");
        if (name == null || name.isEmpty()) {
            return null;
        }

        String normalizedRarity = normalizeRarity(
            firstNonBlank(
                stringValue(rarityTag, "internal_name"),
                stringValue(merged, "rarity", "quality")
            )
        );

        return new BuffItem(
            stringValue(merged, "assetid", "asset_id", "id", "goods_id", "name"),
            name,
            doubleValue(merged, 0.0d, "sell_min_price", "price", "quick_price", "steam_price"),
            nullableDoubleValue(merged, "paintwear", "float_value", "goods_float"),
            stringValue(merged, "paintwear", "float_value", "goods_float"),
            normalizeImageUrl(stringValue(assetInfo, "original_icon_url", "icon_url", "img", "image_url")),
            firstNonBlank(
                stringValue(exteriorTag, "localized_name"),
                stringValue(merged, "exterior", "wear_name", "paintwear_desc", "item_wear")
            ),
            firstNonBlank(
                stringValue(itemsetTag, "localized_name"),
                stringValue(merged, "collection", "collection_name")
            ),
            firstNonBlank(
                stringValue(rarityTag, "internal_name"),
                stringValue(merged, "rarity", "quality")
            ),
            firstNonBlank(
                stringValue(categoryTag, "internal_name"),
                stringValue(weaponTag, "internal_name"),
                stringValue(merged, "category", "category_name")
            ),
            normalizedRarity,
            firstNonBlank(
                stringValue(rarityTag, "localized_name"),
                stringValue(merged, "quality_name", "quality", "rarity_name")
            ),
            booleanValue(merged, "tradable", "can_trade", "allow_exchange"),
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
        if ("ancient_weapon".equals(lowered) || "covert_weapon".equals(lowered)) {
            return "covert";
        }
        if ("legendary_weapon".equals(lowered) || "classified_weapon".equals(lowered)) {
            return "classified";
        }
        if ("mythical_weapon".equals(lowered) || "restricted_weapon".equals(lowered)) {
            return "restricted";
        }
        if ("rare_weapon".equals(lowered) || "milspec_weapon".equals(lowered)) {
            return "mil-spec";
        }
        if ("common_weapon".equals(lowered) || "industrial_weapon".equals(lowered)) {
            return "industrial";
        }
        if ("default_weapon".equals(lowered) || "consumer_weapon".equals(lowered)) {
            return "consumer";
        }
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

    private static String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        return trimmed;
    }

    private static Object mergeFirst(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean booleanValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean) {
                return ((Boolean) value).booleanValue();
            }
            String text = String.valueOf(value).trim().toLowerCase();
            if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
                return true;
            }
            if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
                return false;
            }
        }
        return false;
    }

    private void sleepBeforeNextPage(int nextPage) {
        try {
            log.info("Waiting {} ms before requesting next BUFF inventory page, nextPage={}",
                Long.valueOf(PAGE_REQUEST_INTERVAL_MILLIS), Integer.valueOf(nextPage));
            Thread.sleep(PAGE_REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to request the next BUFF inventory page.", ex);
        }
    }
}
