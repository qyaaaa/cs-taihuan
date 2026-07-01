package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final String CATALOG_COLUMNS = "name, goods_id, collection_name, rarity, category_key, quality_label, min_float, max_float, price, image_url";

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

    // 按字段搜索：collection / name / rarity 各自独立过滤，条件之间为 AND。
    public List<CatalogSkin> searchTargets(String collection, String name, String rarity, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        StringBuilder sql = new StringBuilder(
            "SELECT " + CATALOG_COLUMNS + " FROM catalog_skin WHERE goods_id IS NOT NULL AND goods_id <> ''");
        List<Object> args = new java.util.ArrayList<Object>();
        if (collection != null && !collection.trim().isEmpty()) {
            sql.append(" AND collection_name LIKE ?");
            args.add("%" + collection.trim() + "%");
        }
        if (name != null && !name.trim().isEmpty()) {
            sql.append(" AND name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        if (rarity != null && !rarity.trim().isEmpty()) {
            sql.append(" AND rarity = ?");
            args.add(rarity.trim());
        }
        sql.append(" ORDER BY price DESC, name ASC LIMIT ?");
        args.add(Integer.valueOf(normalizedLimit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapCatalogSkin(rs), args.toArray());
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

    // 找出磨损档不全(同名皮肤少于 5 个外观)且整组都已过期的皮肤，各返回一个锚点 goods_id 及其收藏品。
    // 把锚点重新入队同步后，会通过其 relative_goods 自动发现并补齐缺失的磨损档，不依赖库存可达性。
    public Map<String, String> findIncompleteSkinAnchors(long freshAfterTimestamp, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT MIN(goods_id) AS anchor, MAX(collection_name) AS collection_name " +
                "FROM catalog_skin " +
                "WHERE goods_id IS NOT NULL AND goods_id <> '' " +
                // 纪念品永远不是汰换产物，补全它的磨损档没有意义，只补普通产物。
                "AND name NOT LIKE '%纪念品%' AND name NOT LIKE '%Souvenir%' " +
                "GROUP BY REGEXP_REPLACE(name, ' \\\\([^)]*\\\\)$', '') " +
                "HAVING COUNT(*) < 5 AND MAX(updated_at) < ? " +
                "ORDER BY MAX(updated_at) ASC " +
                "LIMIT " + normalizedLimit,
            Long.valueOf(freshAfterTimestamp)
        );
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

    @Transactional
    public int replaceAll(final List<CatalogSkin> items) {
        log.info("Catalog replace start, itemCount={}", Integer.valueOf(items.size()));
        jdbcTemplate.update("DELETE FROM catalog_skin");

        if (!items.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO catalog_skin (name, goods_id, collection_name, rarity, category_key, quality_label, min_float, max_float, price, image_url, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                        statement.setString(10, skin.getImageUrl());
                        statement.setLong(11, now);
                        statement.setLong(12, now);
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
            "INSERT INTO catalog_skin (name, goods_id, collection_name, rarity, category_key, quality_label, min_float, max_float, price, image_url, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
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
                // 仅在抓到新图标时覆盖，避免某次响应缺图把已有图标抹成 NULL。
                "image_url = COALESCE(VALUES(image_url), image_url), " +
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
                    if (skin.getImageUrl() == null || skin.getImageUrl().trim().isEmpty()) {
                        statement.setNull(10, Types.VARCHAR);
                    } else {
                        statement.setString(10, skin.getImageUrl());
                    }
                    statement.setLong(11, now);
                    statement.setLong(12, now);
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

    /**
     * 将每个目录皮肤名映射到真实收藏品名，用于修正库存里原始收藏品是发放渠道名的情况
     * （例如 armory「武库通行证」）。
     */
    public Map<String, String> nameToCollection() {
        Map<String, String> map = new java.util.HashMap<String, String>();
        jdbcTemplate.query(
            "SELECT name, collection_name FROM catalog_skin WHERE name IS NOT NULL AND collection_name IS NOT NULL",
            (java.sql.ResultSet rs) -> { map.put(rs.getString("name"), rs.getString("collection_name")); }
        );
        return map;
    }

    /** 返回当前目录中指定收藏品和档位下的皮肤名（含磨损后缀）。 */
    public List<String> collectionSkinNames(String collection, String rarity) {
        if (collection == null || collection.trim().isEmpty() || rarity == null || rarity.trim().isEmpty()) {
            return new java.util.ArrayList<String>();
        }
        return jdbcTemplate.query(
            "SELECT name FROM catalog_skin WHERE collection_name = ? AND rarity = ?",
            (rs, rowNum) -> rs.getString("name"),
            collection.trim(), rarity.trim()
        );
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
        skin.setImageUrl(rs.getString("image_url"));
        return skin;
    }
}
