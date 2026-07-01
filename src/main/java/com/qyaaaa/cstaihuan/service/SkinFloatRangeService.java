package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.model.SkinFloatRange;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages the bundled full-catalog skin wear-range library: loads the snapshot into
 * skin_float_range and serves lookups for the float calculator (range fallback + search).
 */
@Service
public class SkinFloatRangeService {
    private static final Logger log = LoggerFactory.getLogger(SkinFloatRangeService.class);
    private static final String SNAPSHOT_PATH = "data/skin-float-range.json";
    private static final String SOURCE = "csgo-api";

    private static final RowMapper<SkinFloatRange> ROW_MAPPER = (rs, rowNum) -> {
        SkinFloatRange row = new SkinFloatRange();
        row.setSkinId(rs.getString("skin_id"));
        row.setPaintIndex(rs.getString("paint_index"));
        row.setNameEn(rs.getString("name_en"));
        row.setNameZh(rs.getString("name_zh"));
        row.setBaseNameEn(rs.getString("base_name_en"));
        row.setBaseNameZh(rs.getString("base_name_zh"));
        row.setWeapon(rs.getString("weapon"));
        row.setRarity(rs.getString("rarity"));
        row.setMinFloat(rs.getDouble("min_float"));
        row.setMaxFloat(rs.getDouble("max_float"));
        row.setCollectionEn(rs.getString("collection_en"));
        row.setCollectionZh(rs.getString("collection_zh"));
        row.setImage(rs.getString("image_url"));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SkinFloatRangeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void seedIfEmpty() {
        try {
            if (count() == 0) {
                int imported = importFromSnapshot();
                log.info("Seeded skin_float_range from snapshot, count={}", imported);
            } else {
                int imported = importMissingFromSnapshot();
                if (imported > 0) {
                    log.info("Added missing skin_float_range rows from snapshot, count={}", imported);
                }
                // Existing rows predate the image_url column; backfill their icons from the snapshot.
                backfillMissingImages();
            }
        } catch (Exception e) {
            log.warn("Skipped skin_float_range seed: {}", e.getMessage());
        }
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM skin_float_range", Integer.class);
        return count == null ? 0 : count.intValue();
    }

    /** Loads the bundled JSON snapshot and replaces the table contents. Returns row count. */
    @Transactional
    public int importFromSnapshot() {
        final List<SkinFloatRange> rows = readPreparedSnapshot();
        jdbcTemplate.update("DELETE FROM skin_float_range");
        insertRows(rows);
        log.info("Imported skin_float_range rows={}", Integer.valueOf(rows.size()));
        return rows.size();
    }

    /**
     * Adds only rows whose snapshot skin_id is not present locally. This keeps existing
     * manually imported/older rows intact while letting new collections become visible.
     */
    @Transactional
    public int importMissingFromSnapshot() {
        List<SkinFloatRange> rows = readPreparedSnapshot();
        Set<String> existingSkinIds = new HashSet<String>(
            jdbcTemplate.query("SELECT skin_id FROM skin_float_range", (rs, rowNum) -> rs.getString("skin_id"))
        );
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
     * Fills image_url for rows that don't have one yet, matching the snapshot by skin_id. Used to
     * retro-fit icons onto rows imported before the image_url column existed. No-op once all set.
     */
    @Transactional
    public int backfillMissingImages() {
        Integer missing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM skin_float_range WHERE image_url IS NULL OR image_url = ''", Integer.class);
        if (missing == null || missing.intValue() == 0) {
            return 0;
        }
        final List<SkinFloatRange> withImage = new ArrayList<SkinFloatRange>();
        for (SkinFloatRange r : readSnapshot()) {
            if (StringUtils.hasText(r.getSkinId()) && StringUtils.hasText(r.getImage())) {
                withImage.add(r);
            }
        }
        if (withImage.isEmpty()) {
            return 0;
        }
        jdbcTemplate.batchUpdate(
            "UPDATE skin_float_range SET image_url = ? WHERE skin_id = ? AND (image_url IS NULL OR image_url = '')",
            new BatchPreparedStatementSetter() {
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    SkinFloatRange r = withImage.get(i);
                    ps.setString(1, r.getImage());
                    ps.setString(2, r.getSkinId());
                }

                public int getBatchSize() {
                    return withImage.size();
                }
            }
        );
        log.info("Backfilled skin_float_range image_url, candidateRows={}, missingBefore={}",
            Integer.valueOf(withImage.size()), missing);
        return missing.intValue();
    }

    private List<SkinFloatRange> readPreparedSnapshot() {
        List<SkinFloatRange> rows = readSnapshot();
        for (SkinFloatRange row : rows) {
            row.setBaseNameEn(WearSuffix.toMatchKey(row.getNameEn()));
            row.setBaseNameZh(WearSuffix.toMatchKey(row.getNameZh()));
            // Normalize the source rarity into the trade-up scheme so it matches catalog_skin
            // and rarity filtering works (knives/gloves -> gold by weapon).
            row.setRarity(SkinRarity.normalize(row.getRarity(), row.getWeapon()));
        }
        return rows;
    }

    private void insertRows(final List<SkinFloatRange> rows) {
        if (rows.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        jdbcTemplate.batchUpdate(
            "INSERT INTO skin_float_range (skin_id, paint_index, name_en, name_zh, base_name_en, base_name_zh, "
                + "weapon, rarity, min_float, max_float, collection_en, collection_zh, image_url, source, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                    SkinFloatRange r = rows.get(i);
                    ps.setString(1, r.getSkinId());
                    ps.setString(2, r.getPaintIndex());
                    ps.setString(3, r.getNameEn());
                    ps.setString(4, r.getNameZh());
                    ps.setString(5, r.getBaseNameEn());
                    ps.setString(6, r.getBaseNameZh());
                    ps.setString(7, r.getWeapon());
                    ps.setString(8, r.getRarity());
                    ps.setDouble(9, r.getMinFloat());
                    ps.setDouble(10, r.getMaxFloat());
                    ps.setString(11, r.getCollectionEn());
                    ps.setString(12, r.getCollectionZh());
                    ps.setString(13, r.getImage());
                    ps.setString(14, SOURCE);
                    ps.setLong(15, now);
                }

                public int getBatchSize() {
                    return rows.size();
                }
            }
        );
    }

    private List<SkinFloatRange> readSnapshot() {
        try (InputStream in = new ClassPathResource(SNAPSHOT_PATH).getInputStream()) {
            SkinFloatRange[] arr = objectMapper.readValue(in, SkinFloatRange[].class);
            List<SkinFloatRange> rows = new ArrayList<SkinFloatRange>(arr.length);
            for (SkinFloatRange r : arr) {
                rows.add(r);
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("读取磨损范围快照失败: " + e.getMessage(), e);
        }
    }

    /** Looks up a wear range by a skin name (any wear/StatTrak variant), matching EN then ZH. */
    public Optional<SkinFloatRange> findByName(String name) {
        String key = WearSuffix.toMatchKey(name);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        List<SkinFloatRange> rows = jdbcTemplate.query(
            "SELECT * FROM skin_float_range WHERE base_name_en = ? OR base_name_zh = ? LIMIT 1",
            ROW_MAPPER, key, key
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Field-scoped search by collection / name keyword / rarity. */
    public List<SkinFloatRange> search(String collection, String name, String rarity, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        StringBuilder sql = new StringBuilder("SELECT * FROM skin_float_range WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (StringUtils.hasText(collection)) {
            sql.append(" AND (collection_zh LIKE ? OR collection_en LIKE ?)");
            String p = "%" + collection.trim() + "%";
            args.add(p);
            args.add(p);
        }
        if (StringUtils.hasText(name)) {
            sql.append(" AND (name_zh LIKE ? OR name_en LIKE ?)");
            String p = "%" + name.trim() + "%";
            args.add(p);
            args.add(p);
        }
        if (StringUtils.hasText(rarity)) {
            sql.append(" AND rarity = ?");
            args.add(rarity.trim());
        }
        sql.append(" ORDER BY name_en ASC LIMIT ?");
        args.add(Integer.valueOf(normalizedLimit));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    /**
     * Maps each skin (normalized zh base name) to its authoritative paint-kit float range [min,max].
     * Trade-up float math needs the skin's full paint range — NOT a wear-tier sub-range — for both
     * normalizing inputs and scaling the output.
     */
    public Map<String, double[]> nameToRange() {
        Map<String, double[]> map = new java.util.HashMap<String, double[]>();
        jdbcTemplate.query(
            "SELECT name_zh, min_float, max_float FROM skin_float_range WHERE name_zh IS NOT NULL AND name_zh <> ''",
            (java.sql.ResultSet rs) -> {
                String key = WearSuffix.toRangeMatchKey(rs.getString("name_zh"));
                double min = rs.getDouble("min_float");
                double max = rs.getDouble("max_float");
                if (!key.isEmpty() && max > min) {
                    map.putIfAbsent(key, new double[] {min, max});
                }
            }
        );
        return map;
    }

    /** Base skin names (no wear suffix) of a collection at a given rarity — the authoritative roster. */
    public List<String> collectionSkinNames(String collectionZh, String rarity) {
        if (!StringUtils.hasText(collectionZh) || !StringUtils.hasText(rarity)) {
            return new ArrayList<String>();
        }
        return jdbcTemplate.query(
            "SELECT DISTINCT name_zh FROM skin_float_range "
                + "WHERE collection_zh = ? AND rarity = ? AND name_zh IS NOT NULL AND name_zh <> ''",
            (rs, rowNum) -> rs.getString("name_zh"),
            collectionZh.trim(), rarity.trim()
        );
    }

    /** Distinct collection names (zh + en) for the frontend dropdown. */
    public List<String[]> listCollections() {
        return jdbcTemplate.query(
            "SELECT DISTINCT collection_zh, collection_en FROM skin_float_range "
                + "WHERE collection_zh IS NOT NULL OR collection_en IS NOT NULL "
                + "ORDER BY collection_zh ASC",
            (rs, rowNum) -> new String[] {rs.getString("collection_zh"), rs.getString("collection_en")}
        );
    }

    /** Groups all collection-backed skins with their authoritative float ranges for the collection browser. */
    public List<Map<String, Object>> listCollectionBrowser() {
        List<SkinFloatRange> rows = jdbcTemplate.query(
            "SELECT * FROM skin_float_range "
                + "WHERE collection_zh IS NOT NULL OR collection_en IS NOT NULL "
                + "ORDER BY collection_zh ASC, collection_en ASC, rarity ASC, name_en ASC",
            ROW_MAPPER
        );
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
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) collection.get("items");
            items.add(toBrowserItem(row));
            // 用收藏品里最大的武器皮肤 paintIndex 作为发布新旧的近似信号：CS 的 paintIndex 大体随时间递增，
            // 排除手套/特工等远高于武器皮肤的特殊编号（>= 5000）避免被带偏。覆盖全部收藏品，含未同步过的新箱。
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
        // 越新的收藏品排越前（按最大武器皮肤 paintIndex 降序，名称兜底保证顺序稳定）。
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
}
