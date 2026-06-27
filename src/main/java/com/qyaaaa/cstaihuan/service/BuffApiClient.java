package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.BuffAccountProfile;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.util.SkinRarity;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BuffApiClient {
    private static final long PAGE_REQUEST_INTERVAL_MILLIS = 5000L;
    private static final Logger log = LoggerFactory.getLogger(BuffApiClient.class);
    private final RestTemplate restTemplate;
    private final com.qyaaaa.cstaihuan.config.BuffProperties buffProperties;

    public BuffApiClient(RestTemplate restTemplate, com.qyaaaa.cstaihuan.config.BuffProperties buffProperties) {
        this.restTemplate = restTemplate;
        this.buffProperties = buffProperties;
    }

    // 武库通行证（armory）把不同箱子的皮肤混成一个收藏品。这里从 goods detail 的 containers 取真实箱标识
    // （去掉 armory 渠道标识），命中配置映射时返回真实中文收藏品名；未命中返回 null，让上层回退到原有取值。
    private String mappedCollectionFromContainers(Object containers) {
        java.util.Map<String, String> mapping = buffProperties == null ? null : buffProperties.getCollectionNameMapping();
        if (mapping == null || mapping.isEmpty() || !(containers instanceof List)) {
            return null;
        }
        for (Object entry : (List<?>) containers) {
            if (entry == null) {
                continue;
            }
            String identifier = String.valueOf(entry).trim();
            if (identifier.isEmpty() || "armory".equalsIgnoreCase(identifier)) {
                continue;
            }
            String mapped = mapping.get(identifier.toLowerCase());
            if (mapped != null && !mapped.trim().isEmpty()) {
                return mapped.trim();
            }
        }
        return null;
    }

    // 武库通行证（armory）只是发放渠道，itemset 标签会统一显示成“武库通行证”，真正的箱子藏在 weaponcase 标签里
    // （internal_name=Fever Case / localized_name=fever case）。命中配置映射时返回真实中文收藏品名（如 热潮收藏品），
    // 未命中返回 null，让上层回退到原有取值。键统一小写，internal_name 与 localized_name 都尝试。
    private String mappedCollectionFromWeaponcase(Map<String, Object> weaponcaseTag) {
        java.util.Map<String, String> mapping = buffProperties == null ? null : buffProperties.getCollectionNameMapping();
        if (mapping == null || mapping.isEmpty()) {
            return null;
        }
        String[] candidateKeys = new String[] {
            stringValue(weaponcaseTag, "internal_name"),
            stringValue(weaponcaseTag, "localized_name")
        };
        for (String key : candidateKeys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String mapped = mapping.get(key.trim().toLowerCase());
            if (mapped != null && !mapped.trim().isEmpty()) {
                return mapped.trim();
            }
        }
        return null;
    }

    public List<BuffItem> fetchInventory(String baseUrl, String cookie, String game, int pageSize, Integer maxPages) {
        return fetchInventory(baseUrl, cookie, game, pageSize, maxPages, null);
    }

    public List<BuffItem> fetchInventory(String baseUrl, String cookie, String game, int pageSize, Integer maxPages, AsyncTaskService.TaskProgress progress) {
        List<BuffItem> items = new ArrayList<BuffItem>();
        int page = 1;
        Integer totalPages = maxPages;
        while (true) {
            if (progress != null) {
                progress.update(progressPercent(page - 1, totalPages), Integer.valueOf(page), totalPages, "正在请求 BUFF 库存第 " + page + " 页。");
            }
            log.info("Requesting BUFF inventory page, game={}, page={}, pageSize={}", game, Integer.valueOf(page), Integer.valueOf(pageSize));
            Map<String, Object> payload = requestInventory(baseUrl, cookie, game, page, pageSize);
            requireAuthenticatedInventoryPayload(payload);
            List<BuffItem> pageItems = extractItems(payload);
            Integer payloadTotalPages = totalPages(payload, pageSize);
            if (payloadTotalPages != null) {
                totalPages = maxPages == null ? payloadTotalPages : Integer.valueOf(Math.min(maxPages.intValue(), payloadTotalPages.intValue()));
            }
            log.info("BUFF inventory page loaded, game={}, page={}, itemCount={}", game, Integer.valueOf(page), Integer.valueOf(pageItems.size()));
            if (progress != null) {
                progress.update(progressPercent(page, totalPages), Integer.valueOf(page), totalPages, "已读取第 " + page + " 页，累计 " + (items.size() + pageItems.size()) + " 件原始库存。");
            }
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
            sleepBeforeNextPage(page + 1, progress);
            page++;
        }
        return items;
    }

    public boolean validateInventorySession(String baseUrl, String cookie, String game) {
        Map<String, Object> payload = requestInventory(baseUrl, cookie, game, 1, 1);
        return isAuthenticatedInventoryPayload(payload);
    }

    public BuffAccountProfile fetchAccountProfileFromInventory(String baseUrl, String cookie, String game) {
        BuffAccountProfile endpointProfile = fetchAccountProfileFromProfileEndpoints(baseUrl, cookie);
        if (hasProfileIdentity(endpointProfile)) {
            return endpointProfile;
        }

        Map<String, Object> payload = requestInventory(baseUrl, cookie, game, 1, 1);
        if (!isAuthenticatedInventoryPayload(payload)) {
            return null;
        }
        BuffAccountProfile profile = extractAccountProfile(payload);
        return profile == null ? new BuffAccountProfile() : profile;
    }

    private BuffAccountProfile fetchAccountProfileFromProfileEndpoints(String baseUrl, String cookie) {
        String[] paths = new String[] {
            // 返回 data.user_info.{nickname,id}，是目前唯一能拿到 BUFF 真实昵称的端点，放最前优先命中。
            "/account/api/user/info/v2",
            "/api/account/info",
            "/api/user/info",
            "/api/user/profile",
            "/api/account/steam_profile"
        };
        for (String path : paths) {
            Map<String, Object> payload = requestOptional(baseUrl + path, cookie, baseUrl + "/account");
            if (payload == null || hasLoginFailureMarker(payload)) {
                continue;
            }
            BuffAccountProfile profile = extractAccountProfile(payload);
            if (hasProfileIdentity(profile)) {
                return profile;
            }
        }
        return null;
    }

    public Map<String, Object> fetchGoodsDetail(String baseUrl, String cookie, String game, String goodsId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/market/goods/info")
            .queryParam("game", game)
            .queryParam("goods_id", goodsId)
            .toUriString();
        log.info("Requesting BUFF goods detail, game={}, goodsId={}", game, goodsId);
        return request(url, cookie, baseUrl + "/market/" + game);
    }

    public CatalogSkin parseCatalogSkinFromGoodsDetail(Map<String, Object> payload, String fallbackCollection) {
        Map<String, Object> data = mapValue(payload.get("data"));
        Map<String, Object> goodsInfo = mapValue(data.get("goods_info"));
        Map<String, Object> info = mapValue(goodsInfo.get("info"));
        Map<String, Object> tags = mapValue(mergeFirst(data.get("tags"), goodsInfo.get("tags"), info.get("tags")));
        Map<String, Object> rarityTag = mapValue(tags.get("rarity"));
        Map<String, Object> categoryTag = mapValue(tags.get("category"));
        Map<String, Object> weaponTag = mapValue(tags.get("weapon"));
        Map<String, Object> itemsetTag = mapValue(tags.get("itemset"));
        Map<String, Object> weaponcaseTag = mapValue(tags.get("weaponcase"));
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        merged.putAll(info);
        merged.putAll(goodsInfo);
        merged.putAll(data);

        String categoryKey = firstNonBlank(
            stringValue(categoryTag, "internal_name"),
            stringValue(weaponTag, "internal_name"),
            stringValue(merged, "category", "category_name")
        );
        if (categoryKey == null || !categoryKey.startsWith("weapon_")) {
            log.info("Skip catalog skin because category is not weapon, categoryKey={}, goodsId={}",
                categoryKey, firstNonBlank(stringValue(merged, "goods_id", "id")));
            return null;
        }

        String rarity = normalizeRarity(
            firstNonBlank(
                stringValue(rarityTag, "internal_name"),
                stringValue(merged, "rarity", "quality")
            )
        );
        // 刀/手套的来源品质并不统一（covert/extraordinary 等），但在汰换体系里它们一律是暗金(gold)，
        // 只能作为暗金合同产物，绝不能当成隐秘(covert)产物混入普通汰换产物池，否则会出现“练出刀”的错误方案。
        if (SkinRarity.isKnifeOrGlove(categoryKey)) {
            rarity = "gold";
        }
        // armory（武库通行证）皮肤的真实收藏品藏在 containers / weaponcase 里且只有英文/internal 名；命中映射时用真实中文收藏品名，
        // 放在 itemset（会显示成“武库通行证”渠道名）之前覆盖错误的归类，未命中则为 null，自动回退到原有取值，不影响其它收藏品。
        String mappedArmoryCollection = firstNonBlank(
            mappedCollectionFromWeaponcase(weaponcaseTag),
            mappedCollectionFromContainers(data.get("containers"))
        );
        String collection = "gold".equals(rarity)
            ? firstNonBlank(
                mappedArmoryCollection,
                stringValue(weaponcaseTag, "localized_name"),
                stringValue(itemsetTag, "localized_name"),
                stringValue(info, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(goodsInfo, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(merged, "collection", "collection_name"),
                fallbackCollection
            )
            : firstNonBlank(
                mappedArmoryCollection,
                stringValue(itemsetTag, "localized_name"),
                stringValue(weaponcaseTag, "localized_name"),
                stringValue(info, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(goodsInfo, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(merged, "collection", "collection_name"),
                fallbackCollection
            );
        String name = firstNonBlank(
            stringValue(merged, "name", "short_name", "market_hash_name")
        );
        if (name == null || collection == null || rarity == null) {
            log.info("Skip catalog skin because required fields are missing, goodsId={}, name={}, collection={}, rarity={}, tagKeys={}, goodsInfoKeys={}, infoKeys={}",
                firstNonBlank(stringValue(merged, "goods_id", "id")), name, collection, rarity, tags.keySet(), goodsInfo.keySet(), info.keySet());
            return null;
        }

        CatalogSkin skin = new CatalogSkin();
        skin.setGoodsId(firstNonBlank(stringValue(merged, "goods_id", "id")));
        skin.setName(name);
        skin.setCollection(collection);
        skin.setRarity(rarity);
        skin.setCategoryKey(categoryKey);
        skin.setQualityLabel(firstNonBlank(
            stringValue(rarityTag, "localized_name"),
            stringValue(merged, "quality_name", "rarity_name")
        ));
        skin.setPrice(doubleValue(merged, 0.0d, "sell_min_price", "quick_price", "price", "steam_price_cny"));
        skin.setImageUrl(normalizeImageUrl(stringValue(merged, "original_icon_url", "icon_url", "img", "image_url")));

        double[] floatRange = extractPaintwearRange(mergeFirst(data.get("paintwear_range"), goodsInfo.get("paintwear_range"), info.get("paintwear_range")));
        skin.setMinFloat(floatRange[0]);
        skin.setMaxFloat(floatRange[1]);
        return skin;
    }

    public List<String> extractRelatedGoodsIds(Map<String, Object> payload) {
        Map<String, Object> data = mapValue(payload.get("data"));
        List<String> goodsIds = new ArrayList<String>();
        collectRelatedGoodsIds(goodsIds, data.get("relative_goods"));
        collectRelatedGoodsIds(goodsIds, data.get("goods_relations"));
        return goodsIds;
    }

    public List<CatalogSkin> extractRelatedCatalogSkins(Map<String, Object> payload, String fallbackCollection) {
        Map<String, Object> data = mapValue(payload.get("data"));
        Map<String, CatalogSkin> skinsByIdentity = new LinkedHashMap<String, CatalogSkin>();
        collectRelatedCatalogSkins(skinsByIdentity, data.get("relative_goods"), fallbackCollection);
        collectRelatedCatalogSkins(skinsByIdentity, data.get("goods_relations"), fallbackCollection);
        return new ArrayList<CatalogSkin>(skinsByIdentity.values());
    }

    private Map<String, Object> requestInventory(String baseUrl, String cookie, String game, int page, int pageSize) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/market/steam_inventory")
            .queryParam("game", game)
            .queryParam("page_num", page)
            .queryParam("page_size", pageSize)
            .toUriString();
        return request(url, cookie, baseUrl + "/market/csgo");
    }

    private Map<String, Object> request(String url, String cookie, String referer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 SpringBoot cs-taihuan");
        headers.set(HttpHeaders.REFERER, referer);
        headers.set("X-Requested-With", "XMLHttpRequest");
        headers.setAccept(MediaType.parseMediaTypes("application/json, text/plain, */*"));

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Object>(headers), Map.class);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("BUFF request rate limited, url={}", url);
            throw new BuffRateLimitException(ErrorMessages.BUFF_RATE_LIMIT);
        } catch (ResourceAccessException ex) {
            log.warn("BUFF request connection was reset, url={}, message={}", url, ex.getMessage());
            throw new BuffRateLimitException(ErrorMessages.BUFF_CONNECTION_RESET);
        } catch (HttpClientErrorException ex) {
            log.error("BUFF request failed, url={}, status={}", url, ex.getStatusCode());
            throw new IllegalStateException(ErrorMessages.buffApiRequestFailed(ex.getStatusCode()));
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.error("BUFF request returned invalid response, url={}, status={}", url, response.getStatusCode());
            throw new IllegalStateException(ErrorMessages.buffApiRequestFailed(response.getStatusCode()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = response.getBody();
        return payload;
    }

    private Map<String, Object> requestOptional(String url, String cookie, String referer) {
        try {
            return request(url, cookie, referer);
        } catch (BuffRateLimitException ex) {
            log.debug("Optional BUFF profile request was rate limited, url={}", url);
            return null;
        } catch (HttpClientErrorException ex) {
            log.debug("Optional BUFF profile request failed, url={}, status={}", url, ex.getStatusCode());
            return null;
        } catch (IllegalStateException ex) {
            log.debug("Optional BUFF profile request returned unusable response, url={}, message={}", url, ex.getMessage());
            return null;
        } catch (RuntimeException ex) {
            log.debug("Optional BUFF profile request could not be parsed, url={}, message={}", url, ex.getMessage());
            return null;
        }
    }

    private void requireAuthenticatedInventoryPayload(Map<String, Object> payload) {
        if (!isAuthenticatedInventoryPayload(payload)) {
            Map<String, Object> data = mapValue(payload == null ? null : payload.get("data"));
            log.warn("BUFF inventory payload is not authenticated, keys={}, dataKeys={}",
                payload == null ? "null" : payload.keySet(), data.keySet());
            throw new IllegalArgumentException(ErrorMessages.BUFF_SESSION_INVALID);
        }
    }

    private boolean isAuthenticatedInventoryPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty() || hasLoginFailureMarker(payload)) {
            return false;
        }
        Object dataObj = payload.get("data");
        if (!(dataObj instanceof Map)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        if (data.isEmpty() || hasLoginFailureMarker(data)) {
            return false;
        }
        return data.containsKey("items")
            || data.containsKey("inventory")
            || data.containsKey("goods_infos")
            || data.containsKey("page_num")
            || data.containsKey("total_page")
            || data.containsKey("total_count");
    }

    private BuffAccountProfile extractAccountProfile(Map<String, Object> payload) {
        Map<String, Object> data = mapValue(payload.get("data"));
        List<Map<String, Object>> candidates = new ArrayList<Map<String, Object>>();
        collectProfileCandidates(candidates, payload);
        collectProfileCandidates(candidates, data);
        candidates.add(payload);
        candidates.add(data);

        for (Map<String, Object> candidate : candidates) {
            String nickname = firstNonBlank(
                stringValue(candidate, "nickname", "nick_name", "user_name", "username", "display_name", "steam_name"),
                stringValue(mapValue(candidate.get("steam_info")), "nickname", "personaname", "steam_name"),
                stringValue(mapValue(candidate.get("profile")), "nickname", "user_name", "username", "display_name", "steam_name")
            );
            String userId = firstNonBlank(
                stringValue(candidate, "user_id", "buff_user_id", "uid", "id", "steamid", "steam_id"),
                stringValue(mapValue(candidate.get("steam_info")), "user_id", "steamid", "steam_id", "id"),
                stringValue(mapValue(candidate.get("profile")), "user_id", "buff_user_id", "uid", "id", "steamid", "steam_id")
            );
            if (nickname != null || userId != null) {
                BuffAccountProfile profile = new BuffAccountProfile();
                profile.setNickname(nickname);
                profile.setBuffUserId(userId);
                return profile;
            }
        }
        return null;
    }

    private boolean hasProfileIdentity(BuffAccountProfile profile) {
        return profile != null && (profile.getNickname() != null || profile.getBuffUserId() != null);
    }

    private void collectProfileCandidates(List<Map<String, Object>> candidates, Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        String[] keys = new String[] {
            "user",
            "user_info",
            "userinfo",
            "account",
            "account_info",
            "profile",
            "steam_profile",
            "steam_info",
            "steam_user_info"
        };
        for (String key : keys) {
            Map<String, Object> candidate = mapValue(source.get(key));
            if (!candidate.isEmpty()) {
                candidates.add(candidate);
            }
        }
    }

    private static boolean hasLoginFailureMarker(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        appendMarkerText(text, payload.get("code"));
        appendMarkerText(text, payload.get("error"));
        appendMarkerText(text, payload.get("error_code"));
        appendMarkerText(text, payload.get("message"));
        appendMarkerText(text, payload.get("msg"));
        appendMarkerText(text, payload.get("status"));
        String marker = text.toString().toLowerCase();
        return marker.contains("login")
            || marker.contains("not_logged")
            || marker.contains("not logged")
            || marker.contains("unauthorized")
            || marker.contains("forbidden")
            || marker.contains("csrf")
            || marker.contains("auth")
            || marker.contains("未登录")
            || marker.contains("请先登录")
            || marker.contains("登录后")
            || marker.contains("会话失效");
    }

    private static void appendMarkerText(StringBuilder target, Object value) {
        if (value != null) {
            target.append(' ').append(String.valueOf(value));
        }
    }

    private double[] extractPaintwearRange(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() >= 2) {
                Double min = objectToDouble(list.get(0));
                Double max = objectToDouble(list.get(1));
                if (min != null && max != null) {
                    return new double[] {min.doubleValue(), max.doubleValue()};
                }
            }
        }
        return new double[] {0.0d, 1.0d};
    }

    private void collectRelatedGoodsIds(List<String> target, Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                collectRelatedGoodsIds(target, item);
            }
            return;
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) value;
            String goodsId = firstNonBlank(
                stringValue(row, "goods_id", "id")
            );
            if (goodsId != null) {
                target.add(goodsId);
            }
            for (Object nested : row.values()) {
                if (nested instanceof Map || nested instanceof List) {
                    collectRelatedGoodsIds(target, nested);
                }
            }
        }
    }

    private void collectRelatedCatalogSkins(Map<String, CatalogSkin> target, Object value, String fallbackCollection) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            for (Object item : list) {
                collectRelatedCatalogSkins(target, item, fallbackCollection);
            }
            return;
        }
        if (!(value instanceof Map)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) value;
        CatalogSkin skin = parseCatalogSkinFromRelatedRow(row, fallbackCollection);
        if (skin != null) {
            String identity = catalogSkinIdentity(skin);
            if (!identity.isEmpty()) {
                target.put(identity, skin);
            }
        }
        for (Object nested : row.values()) {
            if (nested instanceof Map || nested instanceof List) {
                collectRelatedCatalogSkins(target, nested, fallbackCollection);
            }
        }
    }

    private CatalogSkin parseCatalogSkinFromRelatedRow(Map<String, Object> row, String fallbackCollection) {
        Map<String, Object> goodsInfo = mapValue(row.get("goods_info"));
        Map<String, Object> info = mapValue(mergeFirst(row.get("info"), goodsInfo.get("info")));
        Map<String, Object> tags = mapValue(mergeFirst(row.get("tags"), goodsInfo.get("tags"), info.get("tags")));
        Map<String, Object> rarityTag = mapValue(tags.get("rarity"));
        Map<String, Object> categoryTag = mapValue(tags.get("category"));
        Map<String, Object> weaponTag = mapValue(tags.get("weapon"));
        Map<String, Object> itemsetTag = mapValue(tags.get("itemset"));
        Map<String, Object> weaponcaseTag = mapValue(tags.get("weaponcase"));
        Map<String, Object> merged = new LinkedHashMap<String, Object>();
        merged.putAll(info);
        merged.putAll(goodsInfo);
        merged.putAll(row);

        String categoryKey = firstNonBlank(
            stringValue(categoryTag, "internal_name"),
            stringValue(weaponTag, "internal_name"),
            stringValue(merged, "category", "category_name")
        );
        if (categoryKey == null || !categoryKey.startsWith("weapon_")) {
            return null;
        }

        String rarity = normalizeRarity(
            firstNonBlank(
                stringValue(rarityTag, "internal_name"),
                stringValue(merged, "rarity", "quality")
            )
        );
        // 刀/手套的来源品质并不统一（covert/extraordinary 等），但在汰换体系里它们一律是暗金(gold)，
        // 只能作为暗金合同产物，绝不能当成隐秘(covert)产物混入普通汰换产物池，否则会出现“练出刀”的错误方案。
        if (SkinRarity.isKnifeOrGlove(categoryKey)) {
            rarity = "gold";
        }
        String mappedArmoryCollection = firstNonBlank(
            mappedCollectionFromWeaponcase(weaponcaseTag),
            mappedCollectionFromContainers(row.get("containers"))
        );
        String collection = "gold".equals(rarity)
            ? firstNonBlank(
                mappedArmoryCollection,
                stringValue(weaponcaseTag, "localized_name"),
                stringValue(itemsetTag, "localized_name"),
                stringValue(info, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(goodsInfo, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(merged, "collection", "collection_name"),
                fallbackCollection
            )
            : firstNonBlank(
                mappedArmoryCollection,
                stringValue(itemsetTag, "localized_name"),
                stringValue(weaponcaseTag, "localized_name"),
                stringValue(info, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(goodsInfo, "collection", "collection_name", "itemset", "itemset_name", "set_name", "series_name"),
                stringValue(merged, "collection", "collection_name"),
                fallbackCollection
            );
        // 关联产物只接受本地化中文名；缺中文名时跳过，避免落库英文 market_hash_name 重复条目，
        // 把同一皮肤的磨损档拆成两个产物 family 导致产出磨损算错。该 goods 仍会被自身详情接口正确补回。
        String name = firstNonBlank(
            stringValue(merged, "name", "short_name")
        );
        if (name == null || collection == null || rarity == null) {
            return null;
        }

        CatalogSkin skin = new CatalogSkin();
        skin.setGoodsId(firstNonBlank(stringValue(merged, "goods_id", "id")));
        skin.setName(name);
        skin.setCollection(collection);
        skin.setRarity(rarity);
        skin.setCategoryKey(categoryKey);
        skin.setQualityLabel(firstNonBlank(
            stringValue(rarityTag, "localized_name"),
            stringValue(merged, "quality_name", "rarity_name")
        ));
        skin.setPrice(doubleValue(merged, 0.0d, "sell_min_price", "quick_price", "price", "steam_price_cny"));
        skin.setImageUrl(normalizeImageUrl(stringValue(merged, "original_icon_url", "icon_url", "img", "image_url")));

        double[] floatRange = extractPaintwearRange(mergeFirst(row.get("paintwear_range"), goodsInfo.get("paintwear_range"), info.get("paintwear_range")));
        skin.setMinFloat(floatRange[0]);
        skin.setMaxFloat(floatRange[1]);
        return skin;
    }

    private String catalogSkinIdentity(CatalogSkin skin) {
        String identity = firstNonBlank(skin.getGoodsId());
        if (identity == null) {
            identity = firstNonBlank(skin.getName());
        }
        return identity == null ? "" : identity;
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
        Map<String, Object> weaponcaseTag = mapValue(tags.get("weaponcase"));
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
                mappedCollectionFromWeaponcase(weaponcaseTag),
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

    private Integer totalPages(Map<String, Object> payload, int pageSize) {
        Object dataObj = payload.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;
            Integer totalPage = integerValue(data.get("total_page"));
            if (totalPage != null) {
                return totalPage;
            }
            Integer totalCount = integerValue(data.get("total_count"));
            if (totalCount != null) {
                return Integer.valueOf((int) Math.ceil((double) totalCount.intValue() / (double) pageSize));
            }
        }
        return null;
    }

    private int progressPercent(int currentPage, Integer totalPages) {
        if (totalPages == null || totalPages.intValue() <= 0) {
            return Math.min(90, currentPage * 10);
        }
        return Math.min(90, (int) Math.floor((double) currentPage * 90.0d / (double) totalPages.intValue()));
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

    private static Double objectToDouble(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        return Double.valueOf(String.valueOf(value));
    }

    private static String normalizeRarity(String rarity) {
        if (rarity == null) {
            return null;
        }
        String lowered = rarity.trim().toLowerCase();
        if ("ancient_weapon".equals(lowered) || "covert_weapon".equals(lowered)) {
            return "covert";
        }
        if ("gold".equals(lowered)
            || "gold_item".equals(lowered)
            || "exceedingly_rare".equals(lowered)
            || "exceedingly_rare_item".equals(lowered)
            || "rare_special".equals(lowered)
            || "rare_special_item".equals(lowered)
            || "extraordinary".equals(lowered)
            || "extraordinary_item".equals(lowered)
            || "contraband".equals(lowered)
            || "contraband_weapon".equals(lowered)
            || "gold grade".equals(lowered)
            || "exceedingly rare".equals(lowered)) {
            return "gold";
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
        if ("uncommon_weapon".equals(lowered) || "industrial_weapon".equals(lowered)) {
            return "industrial";
        }
        // CS 内部 tier 命名族：common=消费级, uncommon=工业级, rare=军规, mythical=受限, legendary=保密, ancient=隐秘。
        // common_weapon 是消费级而非工业级，早期误归到 industrial，导致整箱消费级皮被错配档位。
        if ("default_weapon".equals(lowered) || "consumer_weapon".equals(lowered) || "common_weapon".equals(lowered)) {
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

    private void sleepBeforeNextPage(int nextPage, AsyncTaskService.TaskProgress progress) {
        try {
            log.info("Waiting {} ms before requesting next BUFF inventory page, nextPage={}",
                Long.valueOf(PAGE_REQUEST_INTERVAL_MILLIS), Integer.valueOf(nextPage));
            if (progress != null) {
                progress.message("等待 " + (PAGE_REQUEST_INTERVAL_MILLIS / 1000L) + " 秒后继续请求第 " + nextPage + " 页，降低 BUFF 限流概率。");
            }
            Thread.sleep(PAGE_REQUEST_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ErrorMessages.BUFF_INVENTORY_PAGE_WAIT_INTERRUPTED, ex);
        }
    }
}
