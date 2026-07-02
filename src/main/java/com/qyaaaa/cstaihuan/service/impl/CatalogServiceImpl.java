package com.qyaaaa.cstaihuan.service.impl;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.mapper.CatalogSkinMapper;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.service.CatalogService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogServiceImpl implements CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogServiceImpl.class);
    private final CatalogSkinMapper catalogSkinMapper;

    public CatalogServiceImpl(CatalogSkinMapper catalogSkinMapper) {
        this.catalogSkinMapper = catalogSkinMapper;
    }

    @Override
    public List<CatalogSkin> loadAll() {
        List<CatalogSkin> items = catalogSkinMapper.selectAllOrdered();
        if (items.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.CATALOG_EMPTY);
        }
        log.info("Catalog loaded from database, itemCount={}", Integer.valueOf(items.size()));
        return items;
    }

    @Override
    public Optional<CatalogSkin> findByGoodsId(String goodsId) {
        return Optional.ofNullable(catalogSkinMapper.selectByGoodsId(goodsId));
    }

    @Override
    public List<CatalogSkin> searchTargets(String collection, String name, String rarity, int limit) {
        return catalogSkinMapper.searchTargets(normalize(collection), normalize(name), normalize(rarity), normalizeLimit(limit, 100));
    }

    @Override
    public List<CatalogSkin> searchTargets(String keyword, int limit) {
        return catalogSkinMapper.searchByKeyword(normalize(keyword), normalizeLimit(limit, 100));
    }

    @Override
    public Set<String> loadExistingGoodsIds() {
        return new LinkedHashSet<String>(catalogSkinMapper.selectExistingGoodsIds());
    }

    @Override
    public Set<String> loadFreshGoodsIds(long freshAfterTimestamp) {
        return new LinkedHashSet<String>(catalogSkinMapper.selectFreshGoodsIds(freshAfterTimestamp));
    }

    /**
     * 找出磨损档不全且整组过期的皮肤锚点，重新入队后由 BUFF relative_goods 补齐同皮肤其它磨损档。
     */
    @Override
    public Map<String, String> findIncompleteSkinAnchors(long freshAfterTimestamp, int limit) {
        List<Map<String, Object>> rows = catalogSkinMapper.selectIncompleteSkinAnchors(freshAfterTimestamp, normalizeLimit(limit, 500));
        Map<String, String> anchors = new LinkedHashMap<String, String>();
        for (Map<String, Object> row : rows) {
            Object anchor = row.get("anchor");
            if (anchor != null && !String.valueOf(anchor).trim().isEmpty()) {
                Object collection = row.get("collection_name");
                anchors.put(String.valueOf(anchor).trim(), collection == null ? null : String.valueOf(collection));
            }
        }
        return anchors;
    }

    @Override
    @Transactional
    public int replaceAll(List<CatalogSkin> items) {
        List<CatalogSkin> safeItems = items == null ? new ArrayList<CatalogSkin>() : items;
        log.info("Catalog replace start, itemCount={}", Integer.valueOf(safeItems.size()));
        catalogSkinMapper.deleteAll();
        if (!safeItems.isEmpty()) {
            prepareForPersist(safeItems);
            catalogSkinMapper.insertBatch(safeItems);
        }
        log.info("Catalog replace finished, persistedItemCount={}", Integer.valueOf(safeItems.size()));
        return safeItems.size();
    }

    /**
     * 增量同步按唯一键 upsert；空 image_url 不覆盖已有图片，避免 BUFF 某次缺图导致前端图鉴退化。
     */
    @Override
    @Transactional
    public int upsertAll(List<CatalogSkin> items) {
        if (items == null || items.isEmpty()) {
            log.info("Catalog upsert skipped, itemCount=0");
            return 0;
        }
        log.info("Catalog upsert start, itemCount={}", Integer.valueOf(items.size()));
        prepareForPersist(items);
        catalogSkinMapper.upsertBatch(items);
        log.info("Catalog upsert finished, itemCount={}", Integer.valueOf(items.size()));
        return items.size();
    }

    @Override
    public int count() {
        return Math.toIntExact(catalogSkinMapper.selectCount(null));
    }

    @Override
    public Map<String, String> nameToCollection() {
        Map<String, String> map = new java.util.HashMap<String, String>();
        for (CatalogSkin skin : catalogSkinMapper.selectNameCollections()) {
            map.put(skin.getName(), skin.getCollection());
        }
        return map;
    }

    @Override
    public List<String> collectionSkinNames(String collection, String rarity) {
        String normalizedCollection = normalize(collection);
        String normalizedRarity = normalize(rarity);
        if (normalizedCollection == null || normalizedRarity == null) {
            return new ArrayList<String>();
        }
        return catalogSkinMapper.selectSkinNamesByCollectionAndRarity(normalizedCollection, normalizedRarity);
    }

    private void prepareForPersist(List<CatalogSkin> items) {
        long now = System.currentTimeMillis();
        for (CatalogSkin skin : items) {
            skin.setGoodsId(normalize(skin.getGoodsId()));
            skin.setImageUrl(normalize(skin.getImageUrl()));
            if (skin.getCreatedAt() == null) {
                skin.setCreatedAt(Long.valueOf(now));
            }
            skin.setUpdatedAt(Long.valueOf(now));
        }
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private int normalizeLimit(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }
}
