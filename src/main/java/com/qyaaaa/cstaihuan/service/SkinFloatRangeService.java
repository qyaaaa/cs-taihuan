package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.model.SkinFloatRange;
import com.qyaaaa.cstaihuan.util.WearSuffix;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        final List<SkinFloatRange> rows = readSnapshot();
        for (SkinFloatRange row : rows) {
            row.setBaseNameEn(WearSuffix.toMatchKey(row.getNameEn()));
            row.setBaseNameZh(WearSuffix.toMatchKey(row.getNameZh()));
        }
        jdbcTemplate.update("DELETE FROM skin_float_range");
        if (!rows.isEmpty()) {
            final long now = System.currentTimeMillis();
            jdbcTemplate.batchUpdate(
                "INSERT INTO skin_float_range (skin_id, paint_index, name_en, name_zh, base_name_en, base_name_zh, "
                    + "weapon, rarity, min_float, max_float, collection_en, collection_zh, source, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                        ps.setString(13, SOURCE);
                        ps.setLong(14, now);
                    }

                    public int getBatchSize() {
                        return rows.size();
                    }
                }
            );
        }
        log.info("Imported skin_float_range rows={}", Integer.valueOf(rows.size()));
        return rows.size();
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

    /** Field-scoped search by collection and/or name keyword. */
    public List<SkinFloatRange> search(String collection, String name, int limit) {
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
        sql.append(" ORDER BY name_en ASC LIMIT ?");
        args.add(Integer.valueOf(normalizedLimit));
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
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
}
