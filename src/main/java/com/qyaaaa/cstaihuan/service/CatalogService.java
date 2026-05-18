package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final JdbcTemplate jdbcTemplate;
    private static final String CATALOG_COLUMNS = "name, goods_id, collection_name, rarity, category_key, quality_label, min_float, max_float, price";

    public CatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CatalogSkin> loadAll() {
        List<CatalogSkin> items = jdbcTemplate.query(
            "SELECT " + CATALOG_COLUMNS + " FROM catalog_skin ORDER BY collection_name ASC, rarity ASC, name ASC",
            (rs, rowNum) -> mapCatalogSkin(rs)
        );
        if (items.isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.CATALOG_EMPTY);
        }
        log.info("Catalog loaded from database, itemCount={}", Integer.valueOf(items.size()));
        return items;
    }

    public Optional<CatalogSkin> findByGoodsId(String goodsId) {
        List<CatalogSkin> rows = jdbcTemplate.query(
            "SELECT " + CATALOG_COLUMNS + " FROM catalog_skin WHERE goods_id = ? LIMIT 1",
            (rs, rowNum) -> mapCatalogSkin(rs),
            goodsId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<CatalogSkin> searchTargets(String keyword, int limit) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        if (normalizedKeyword.isEmpty()) {
            return jdbcTemplate.query(
                "SELECT " + CATALOG_COLUMNS + " FROM catalog_skin WHERE goods_id IS NOT NULL AND goods_id <> '' ORDER BY updated_at DESC, price DESC LIMIT ?",
                (rs, rowNum) -> mapCatalogSkin(rs),
                Integer.valueOf(normalizedLimit)
            );
        }
        String pattern = "%" + normalizedKeyword + "%";
        return jdbcTemplate.query(
            "SELECT " + CATALOG_COLUMNS + " FROM catalog_skin WHERE goods_id IS NOT NULL AND goods_id <> '' AND (name LIKE ? OR goods_id LIKE ? OR collection_name LIKE ?) ORDER BY price DESC, name ASC LIMIT ?",
            (rs, rowNum) -> mapCatalogSkin(rs),
            pattern,
            pattern,
            pattern,
            Integer.valueOf(normalizedLimit)
        );
    }

    public Set<String> loadExistingGoodsIds() {
        List<String> rows = jdbcTemplate.query(
            "SELECT goods_id FROM catalog_skin WHERE goods_id IS NOT NULL AND goods_id <> ''",
            (rs, rowNum) -> rs.getString("goods_id")
        );
        return new LinkedHashSet<String>(rows);
    }

    public Set<String> loadFreshGoodsIds(long freshAfterTimestamp) {
        List<String> rows = jdbcTemplate.query(
            "SELECT goods_id FROM catalog_skin WHERE goods_id IS NOT NULL AND goods_id <> '' AND updated_at >= ?",
            (rs, rowNum) -> rs.getString("goods_id"),
            Long.valueOf(freshAfterTimestamp)
        );
        return new LinkedHashSet<String>(rows);
    }

    @Transactional
    public int replaceAll(final List<CatalogSkin> items) {
        log.info("Catalog replace start, itemCount={}", Integer.valueOf(items.size()));
        jdbcTemplate.update("DELETE FROM catalog_skin");

        if (!items.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO catalog_skin (name, goods_id, collection_name, rarity, category_key, quality_label, min_float, max_float, price, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    public void setValues(java.sql.PreparedStatement statement, int index) throws java.sql.SQLException {
                        CatalogSkin skin = items.get(index);
                        long now = System.currentTimeMillis();
                        statement.setString(1, skin.getName());
                        statement.setString(2, skin.getGoodsId());
                        statement.setString(3, skin.getCollection());
                        statement.setString(4, skin.getRarity());
                        statement.setString(5, skin.getCategoryKey());
                        statement.setString(6, skin.getQualityLabel());
                        statement.setDouble(7, skin.getMinFloat());
                        statement.setDouble(8, skin.getMaxFloat());
                        statement.setBigDecimal(9, java.math.BigDecimal.valueOf(skin.getPrice()));
                        statement.setLong(10, now);
                        statement.setLong(11, now);
                    }

                    public int getBatchSize() {
                        return items.size();
                    }
                }
            );
        }
        log.info("Catalog replace finished, persistedItemCount={}", Integer.valueOf(items.size()));
        return items.size();
    }

    @Transactional
    public int upsertAll(final List<CatalogSkin> items) {
        if (items.isEmpty()) {
            log.info("Catalog upsert skipped, itemCount=0");
            return 0;
        }
        log.info("Catalog upsert start, itemCount={}", Integer.valueOf(items.size()));
        jdbcTemplate.batchUpdate(
            "INSERT INTO catalog_skin (name, goods_id, collection_name, rarity, category_key, quality_label, min_float, max_float, price, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "name = VALUES(name), " +
                "goods_id = VALUES(goods_id), " +
                "collection_name = VALUES(collection_name), " +
                "rarity = VALUES(rarity), " +
                "category_key = VALUES(category_key), " +
                "quality_label = VALUES(quality_label), " +
                "min_float = VALUES(min_float), " +
                "max_float = VALUES(max_float), " +
                "price = VALUES(price), " +
                "updated_at = VALUES(updated_at)",
            new BatchPreparedStatementSetter() {
                public void setValues(java.sql.PreparedStatement statement, int index) throws java.sql.SQLException {
                    CatalogSkin skin = items.get(index);
                    long now = System.currentTimeMillis();
                    statement.setString(1, skin.getName());
                    if (skin.getGoodsId() == null || skin.getGoodsId().trim().isEmpty()) {
                        statement.setNull(2, Types.VARCHAR);
                    } else {
                        statement.setString(2, skin.getGoodsId());
                    }
                    statement.setString(3, skin.getCollection());
                    statement.setString(4, skin.getRarity());
                    statement.setString(5, skin.getCategoryKey());
                    statement.setString(6, skin.getQualityLabel());
                    statement.setDouble(7, skin.getMinFloat());
                    statement.setDouble(8, skin.getMaxFloat());
                    statement.setBigDecimal(9, java.math.BigDecimal.valueOf(skin.getPrice()));
                    statement.setLong(10, now);
                    statement.setLong(11, now);
                }

                public int getBatchSize() {
                    return items.size();
                }
            }
        );
        log.info("Catalog upsert finished, itemCount={}", Integer.valueOf(items.size()));
        return items.size();
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM catalog_skin", Integer.class);
        return count == null ? 0 : count.intValue();
    }

    private CatalogSkin mapCatalogSkin(java.sql.ResultSet rs) throws java.sql.SQLException {
        CatalogSkin skin = new CatalogSkin();
        skin.setName(rs.getString("name"));
        skin.setGoodsId(rs.getString("goods_id"));
        skin.setCollection(rs.getString("collection_name"));
        skin.setRarity(rs.getString("rarity"));
        skin.setCategoryKey(rs.getString("category_key"));
        skin.setQualityLabel(rs.getString("quality_label"));
        skin.setMinFloat(rs.getDouble("min_float"));
        skin.setMaxFloat(rs.getDouble("max_float"));
        skin.setPrice(rs.getDouble("price"));
        return skin;
    }
}
