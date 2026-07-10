package com.qyaaaa.cstaihuan.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.mapper.SkinFloatRangeMapper;
import com.qyaaaa.cstaihuan.model.SkinFloatRange;
import com.qyaaaa.cstaihuan.service.SkinFloatRangeService;
import com.qyaaaa.cstaihuan.util.SkinRarity;
import com.qyaaaa.cstaihuan.util.WearSuffix;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理内置全量皮肤磨损范围基准库：把快照导入 skin_float_range，
 * 并为磨损计算器提供范围回退和搜索能力。
 */
@Service
public class SkinFloatRangeServiceImpl implements SkinFloatRangeService {
    private static final Logger log = LoggerFactory.getLogger(SkinFloatRangeServiceImpl.class);
    private static final String SNAPSHOT_PATH = "data/skin-float-range.json";
    private static final String SOURCE = "csgo-api";
    // 批量补图每条 UPDATE 覆盖的行数上限，防止 SQL 过长。
    private static final int UPDATE_BATCH_SIZE = 1000;

    private final SkinFloatRangeMapper skinFloatRangeMapper;
    private final ObjectMapper objectMapper;

    public SkinFloatRangeServiceImpl(SkinFloatRangeMapper skinFloatRangeMapper, ObjectMapper objectMapper) {
        this.skinFloatRangeMapper = skinFloatRangeMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void seedIfEmpty() {
        try {
            if (count() == 0) {
                int imported = importFromSnapshot();
                log.info("Seeded skin_float_range from snapshot, count={}", Integer.valueOf(imported));
            } else {
                int imported = importMissingFromSnapshot();
                if (imported > 0) {
                    log.info("Added missing skin_float_range rows from snapshot, count={}", Integer.valueOf(imported));
                }
                // 旧行可能早于 image_url 字段存在，从快照回填图标。
                backfillMissingImages();
                backfillMissingReleaseDates();
            }
        } catch (Exception e) {
            log.warn("Skipped skin_float_range seed: {}", e.getMessage());
        }
    }

    @Override
    public int count() {
        return Math.toIntExact(skinFloatRangeMapper.selectCount(null));
    }

    /** 读取内置 JSON 快照并替换整张表，返回导入行数。 */
    @Override
    @Transactional
    public int importFromSnapshot() {
        List<SkinFloatRange> rows = readPreparedSnapshot();
        skinFloatRangeMapper.deleteAll();
        insertRows(rows);
        log.info("Imported skin_float_range rows={}", Integer.valueOf(rows.size()));
        return rows.size();
    }

    /**
     * 只追加本地尚不存在的快照 skin_id。这样既保留手动导入或旧数据，
     * 又能让新增收藏品自动可见。
     */
    @Override
    @Transactional
    public int importMissingFromSnapshot() {
        List<SkinFloatRange> rows = readPreparedSnapshot();
        Set<String> existingSkinIds = new HashSet<String>(skinFloatRangeMapper.selectSkinIds());
        List<SkinFloatRange> missingRows = new ArrayList<SkinFloatRange>();
        for (SkinFloatRange row : rows) {
            if (StringUtils.hasText(row.getSkinId()) && !existingSkinIds.contains(row.getSkinId())) {
                missingRows.add(row);
            }
        }
        insertRows(missingRows);
        return missingRows.size();
    }

    /**
     * 按 skin_id 匹配快照，为尚无 image_url 的行补图标。全部补齐后再次调用不会产生变化。
     */
    @Override
    @Transactional
    public int backfillMissingImages() {
        Set<String> missingSkinIds = new HashSet<String>(skinFloatRangeMapper.selectMissingImageSkinIds());
        if (missingSkinIds.isEmpty()) {
            return 0;
        }
        List<SkinFloatRange> withImage = new ArrayList<SkinFloatRange>();
        for (SkinFloatRange row : readSnapshot()) {
            if (StringUtils.hasText(row.getSkinId()) && StringUtils.hasText(row.getImage())
                    && missingSkinIds.contains(row.getSkinId())) {
                withImage.add(row);
            }
        }
        if (withImage.isEmpty()) {
            return 0;
        }
        // 分批用单条多行 UPDATE 补图，避免逐行更新的数据库往返开销。
        for (int start = 0; start < withImage.size(); start += UPDATE_BATCH_SIZE) {
            int end = Math.min(start + UPDATE_BATCH_SIZE, withImage.size());
            skinFloatRangeMapper.updateImagesBySkinId(withImage.subList(start, end));
        }
        log.info("Backfilled skin_float_range image_url, candidateRows={}, missingBefore={}",
            Integer.valueOf(withImage.size()), Integer.valueOf(missingSkinIds.size()));
        return missingSkinIds.size();
    }

    /**
     * 按 skin_id 匹配快照，为尚无 release_date 的行补上线日期。上游无日期的老收藏品会一直为空，属预期。
     */
    @Override
    @Transactional
    public int backfillMissingReleaseDates() {
        Set<String> missingSkinIds = new HashSet<String>(skinFloatRangeMapper.selectMissingReleaseDateSkinIds());
        if (missingSkinIds.isEmpty()) {
            return 0;
        }
        List<SkinFloatRange> withDate = new ArrayList<SkinFloatRange>();
        for (SkinFloatRange row : readSnapshot()) {
            if (StringUtils.hasText(row.getSkinId()) && StringUtils.hasText(row.getReleaseDate())
                    && missingSkinIds.contains(row.getSkinId())) {
                withDate.add(row);
            }
        }
        if (withDate.isEmpty()) {
            return 0;
        }
        for (int start = 0; start < withDate.size(); start += UPDATE_BATCH_SIZE) {
            int end = Math.min(start + UPDATE_BATCH_SIZE, withDate.size());
            skinFloatRangeMapper.updateReleaseDatesBySkinId(withDate.subList(start, end));
        }
        log.info("Backfilled skin_float_range release_date, candidateRows={}", Integer.valueOf(withDate.size()));
        return withDate.size();
    }

    private List<SkinFloatRange> readPreparedSnapshot() {
        List<SkinFloatRange> rows = readSnapshot();
        for (SkinFloatRange row : rows) {
            row.setBaseNameEn(WearSuffix.toMatchKey(row.getNameEn()));
            row.setBaseNameZh(WearSuffix.toMatchKey(row.getNameZh()));
            // 将来源稀有度归一到汰换档位体系，确保与 catalog_skin 一致，档位筛选也能正确工作（刀/手套按武器归为 gold）。
            row.setRarity(SkinRarity.normalize(row.getRarity(), row.getWeapon()));
        }
        return rows;
    }

    private void insertRows(List<SkinFloatRange> rows) {
        if (rows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (SkinFloatRange row : rows) {
            row.setSource(SOURCE);
            row.setUpdatedAt(Long.valueOf(now));
        }
        skinFloatRangeMapper.insertBatch(rows);
    }

    private List<SkinFloatRange> readSnapshot() {
        try (InputStream in = new ClassPathResource(SNAPSHOT_PATH).getInputStream()) {
            SkinFloatRange[] arr = objectMapper.readValue(in, SkinFloatRange[].class);
            List<SkinFloatRange> rows = new ArrayList<SkinFloatRange>(arr.length);
            for (SkinFloatRange row : arr) {
                rows.add(row);
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("读取磨损范围快照失败: " + e.getMessage(), e);
        }
    }

    /** 按皮肤名查找磨损范围（支持任意磨损/StatTrak 变体），先匹配英文再匹配中文。 */
    @Override
    public Optional<SkinFloatRange> findByName(String name) {
        String key = WearSuffix.toMatchKey(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skinFloatRangeMapper.selectByBaseName(key));
    }

    /** 按收藏品、名称关键词、档位分别过滤搜索。 */
    @Override
    public List<SkinFloatRange> search(String collection, String name, String rarity, int limit) {
        return skinFloatRangeMapper.search(normalize(collection), normalize(name), normalize(rarity), normalizeLimit(limit, 100));
    }

    /**
     * 将每个皮肤（规范化中文基础名）映射到权威 paint kit 磨损范围 [min,max]。
     * 汰换磨损计算需要皮肤完整 paint 范围，而不是某个外观档子范围，用于归一化输入和缩放产出。
     */
    @Override
    public Map<String, double[]> nameToRange() {
        Map<String, double[]> map = new java.util.HashMap<String, double[]>();
        for (SkinFloatRange row : skinFloatRangeMapper.selectNameRanges()) {
            String key = WearSuffix.toRangeMatchKey(row.getNameZh());
            double min = row.getMinFloat();
            double max = row.getMaxFloat();
            if (!key.isEmpty() && max > min) {
                map.putIfAbsent(key, new double[] {min, max});
            }
        }
        return map;
    }

    /** 返回指定收藏品和档位下的基础皮肤名（不含磨损后缀），作为权威名单。 */
    @Override
    public List<String> collectionSkinNames(String collectionZh, String rarity) {
        String normalizedCollection = normalize(collectionZh);
        String normalizedRarity = normalize(rarity);
        if (normalizedCollection == null || normalizedRarity == null) {
            return new ArrayList<String>();
        }
        return skinFloatRangeMapper.selectSkinNamesByCollectionAndRarity(normalizedCollection, normalizedRarity);
    }

    /** 返回去重收藏品中英文名，供前端下拉框使用。 */
    @Override
    public List<String[]> listCollections() {
        List<String[]> result = new ArrayList<String[]>();
        for (Map<String, String> row : skinFloatRangeMapper.selectCollections()) {
            result.add(new String[] {nullableString(row.get("collectionZh")), nullableString(row.get("collectionEn"))});
        }
        return result;
    }

    /** 按收藏品分组返回所有皮肤及其权威磨损范围，供收藏品图鉴使用。 */
    @Override
    public List<Map<String, Object>> listCollectionBrowser() {
        List<SkinFloatRange> rows = skinFloatRangeMapper.selectCollectionBrowserRows();
        Map<String, Map<String, Object>> byCollection = new LinkedHashMap<String, Map<String, Object>>();
        Map<String, Long> recencyByKey = new java.util.HashMap<String, Long>();
        for (SkinFloatRange row : rows) {
            String key = collectionKey(row);
            Map<String, Object> collection = byCollection.get(key);
            if (collection == null) {
                collection = new LinkedHashMap<String, Object>();
                collection.put("key", key);
                collection.put("nameZh", row.getCollectionZh());
                collection.put("nameEn", row.getCollectionEn());
                collection.put("items", new ArrayList<Map<String, Object>>());
                byCollection.put(key, collection);
            }
            if (collection.get("releaseDate") == null && StringUtils.hasText(row.getReleaseDate())) {
                collection.put("releaseDate", row.getReleaseDate());
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) collection.get("items");
            items.add(toBrowserItem(row));
            // 用收藏品里最大的武器皮肤 paintIndex 作为发布新旧的近似信号；排除手套/特工等特殊编号避免排序被带偏。
            long paint = parseWeaponPaintIndex(row.getPaintIndex());
            Long current = recencyByKey.get(key);
            if (current == null || paint > current.longValue()) {
                recencyByKey.put(key, Long.valueOf(paint));
            }
        }
        for (Map<String, Object> collection : byCollection.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) collection.get("items");
            collection.put("itemCount", Integer.valueOf(items.size()));
            collection.put("rarities", collectRarities(items));
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(byCollection.values());
        result.sort((left, right) -> {
            long leftKey = recencyByKey.getOrDefault(String.valueOf(left.get("key")), Long.valueOf(0L)).longValue();
            long rightKey = recencyByKey.getOrDefault(String.valueOf(right.get("key")), Long.valueOf(0L)).longValue();
            if (leftKey != rightKey) {
                return Long.compare(rightKey, leftKey);
            }
            return String.valueOf(left.get("nameZh")).compareTo(String.valueOf(right.get("nameZh")));
        });
        return result;
    }

    // 解析武器皮肤的 paintIndex；排除手套/特工等远高于武器皮肤的特殊编号（>= 5000），非数字返回 0。
    private long parseWeaponPaintIndex(String paintIndex) {
        if (paintIndex == null) {
            return 0L;
        }
        try {
            long value = Long.parseLong(paintIndex.trim());
            return value >= 5000L ? 0L : value;
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String collectionKey(SkinFloatRange row) {
        if (StringUtils.hasText(row.getCollectionZh())) {
            return row.getCollectionZh();
        }
        if (StringUtils.hasText(row.getCollectionEn())) {
            return row.getCollectionEn();
        }
        return "unknown";
    }

    private Map<String, Object> toBrowserItem(SkinFloatRange row) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("skinId", row.getSkinId());
        item.put("paintIndex", row.getPaintIndex());
        item.put("nameZh", row.getNameZh());
        item.put("nameEn", row.getNameEn());
        item.put("weapon", row.getWeapon());
        item.put("rarity", row.getRarity());
        item.put("minFloat", Double.valueOf(row.getMinFloat()));
        item.put("maxFloat", Double.valueOf(row.getMaxFloat()));
        item.put("image", row.getImage());
        return item;
    }

    private List<String> collectRarities(List<Map<String, Object>> items) {
        List<String> rarities = new ArrayList<String>();
        for (Map<String, Object> item : items) {
            String rarity = String.valueOf(item.get("rarity"));
            if (StringUtils.hasText(rarity) && !rarities.contains(rarity)) {
                rarities.add(rarity);
            }
        }
        return rarities;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int normalizeLimit(int limit, int max) {
        return Math.max(1, Math.min(limit, max));
    }
}
