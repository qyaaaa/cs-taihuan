package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotSummary;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventorySnapshotStoreService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InventorySnapshotStoreService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<InventorySnapshotRecord> findLatest(String game) {
        List<InventorySnapshotRecord> records = jdbcTemplate.query(
            "SELECT id, game, item_count, fingerprint, created_at, last_seen_at FROM inventory_snapshot WHERE game = ? ORDER BY created_at DESC LIMIT 1",
            new Object[] {game},
            (rs, rowNum) -> {
                InventorySnapshotRecord record = new InventorySnapshotRecord();
                record.setId(rs.getLong("id"));
                record.setGame(rs.getString("game"));
                record.setItemCount(rs.getInt("item_count"));
                record.setFingerprint(rs.getString("fingerprint"));
                record.setCreatedAt(rs.getLong("created_at"));
                record.setLastSeenAt(rs.getLong("last_seen_at"));
                return record;
            }
        );
        return records.isEmpty() ? Optional.<InventorySnapshotRecord>empty() : Optional.of(records.get(0));
    }

    public Optional<InventorySnapshotRecord> findById(long snapshotId) {
        List<InventorySnapshotRecord> records = jdbcTemplate.query(
            "SELECT id, game, item_count, fingerprint, created_at, last_seen_at FROM inventory_snapshot WHERE id = ?",
            new Object[] {Long.valueOf(snapshotId)},
            (rs, rowNum) -> {
                InventorySnapshotRecord record = new InventorySnapshotRecord();
                record.setId(rs.getLong("id"));
                record.setGame(rs.getString("game"));
                record.setItemCount(rs.getInt("item_count"));
                record.setFingerprint(rs.getString("fingerprint"));
                record.setCreatedAt(rs.getLong("created_at"));
                record.setLastSeenAt(rs.getLong("last_seen_at"));
                return record;
            }
        );
        return records.isEmpty() ? Optional.<InventorySnapshotRecord>empty() : Optional.of(records.get(0));
    }

    public List<BuffItem> loadItems(long snapshotId) {
        return jdbcTemplate.query(
            "SELECT asset_id, goods_id, name, price, float_value, float_value_raw, image_url, wear_name, collection_name, rarity, quality_label, tradable, raw_json FROM inventory_item WHERE snapshot_id = ? ORDER BY id ASC",
            new Object[] {Long.valueOf(snapshotId)},
            (rs, rowNum) -> {
                BuffItem item = new BuffItem();
                item.setAssetId(rs.getString("asset_id"));
                item.setGoodsId(rs.getString("goods_id"));
                item.setName(rs.getString("name"));
                item.setPrice(rs.getBigDecimal("price").doubleValue());
                Object floatValue = rs.getObject("float_value");
                item.setFloatValue(floatValue == null ? null : Double.valueOf(rs.getDouble("float_value")));
                item.setFloatValueRaw(rs.getString("float_value_raw"));
                item.setImageUrl(rs.getString("image_url"));
                item.setWearName(rs.getString("wear_name"));
                item.setCollection(rs.getString("collection_name"));
                item.setRarity(rs.getString("rarity"));
                item.setQualityLabel(rs.getString("quality_label"));
                item.setTradable(rs.getBoolean("tradable"));
                item.setRaw(readRaw(rs.getString("raw_json")));
                return item;
            }
        );
    }

    public List<BuffItem> loadPagedItems(long snapshotId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbcTemplate.query(
            "SELECT asset_id, goods_id, name, price, float_value, float_value_raw, image_url, wear_name, collection_name, rarity, quality_label, tradable, raw_json " +
                "FROM inventory_item WHERE snapshot_id = ? ORDER BY price DESC, id ASC LIMIT ? OFFSET ?",
            new Object[] {Long.valueOf(snapshotId), Integer.valueOf(pageSize), Integer.valueOf(offset)},
            (rs, rowNum) -> {
                BuffItem item = new BuffItem();
                item.setAssetId(rs.getString("asset_id"));
                item.setGoodsId(rs.getString("goods_id"));
                item.setName(rs.getString("name"));
                item.setPrice(rs.getBigDecimal("price").doubleValue());
                Object floatValue = rs.getObject("float_value");
                item.setFloatValue(floatValue == null ? null : Double.valueOf(rs.getDouble("float_value")));
                item.setFloatValueRaw(rs.getString("float_value_raw"));
                item.setImageUrl(rs.getString("image_url"));
                item.setWearName(rs.getString("wear_name"));
                item.setCollection(rs.getString("collection_name"));
                item.setRarity(rs.getString("rarity"));
                item.setQualityLabel(rs.getString("quality_label"));
                item.setTradable(rs.getBoolean("tradable"));
                item.setRaw(readRaw(rs.getString("raw_json")));
                return item;
            }
        );
    }

    public int countItems(long snapshotId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM inventory_item WHERE snapshot_id = ?",
            Integer.class,
            Long.valueOf(snapshotId)
        );
        return count == null ? 0 : count.intValue();
    }

    public InventorySnapshotSummary summarizeSnapshot(long snapshotId) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) AS item_count, SUM(CASE WHEN tradable = 1 THEN 1 ELSE 0 END) AS tradable_count, SUM(CASE WHEN float_value IS NOT NULL THEN 1 ELSE 0 END) AS with_float_count, COALESCE(SUM(price), 0) AS total_cost FROM inventory_item WHERE snapshot_id = ?",
            new Object[] {Long.valueOf(snapshotId)},
            (rs, rowNum) -> {
                InventorySnapshotSummary summary = new InventorySnapshotSummary();
                summary.setItemCount(rs.getInt("item_count"));
                summary.setTradableCount(rs.getInt("tradable_count"));
                summary.setWithFloatCount(rs.getInt("with_float_count"));
                summary.setTotalCost(rs.getDouble("total_cost"));
                return summary;
            }
        );
    }

    @Transactional
    public InventorySnapshotRecord saveSnapshot(String game, String fingerprint, List<BuffItem> items) {
        final long now = System.currentTimeMillis();
        final KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO inventory_snapshot (game, item_count, fingerprint, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, game);
            statement.setInt(2, items.size());
            statement.setString(3, fingerprint);
            statement.setLong(4, now);
            statement.setLong(5, now);
            return statement;
        }, keyHolder);

        final Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create inventory snapshot.");
        }
        final long snapshotId = key.longValue();

        if (!items.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO inventory_item (snapshot_id, asset_id, goods_id, name, price, float_value, float_value_raw, image_url, wear_name, collection_name, rarity, quality_label, tradable, raw_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    public void setValues(PreparedStatement statement, int index) throws java.sql.SQLException {
                        BuffItem item = items.get(index);
                        statement.setLong(1, snapshotId);
                        statement.setString(2, item.getAssetId());
                        statement.setString(3, item.getGoodsId());
                        statement.setString(4, item.getName());
                        statement.setBigDecimal(5, BigDecimal.valueOf(item.getPrice()));
                        if (item.getFloatValue() == null) {
                            statement.setNull(6, java.sql.Types.DOUBLE);
                        } else {
                            statement.setDouble(6, item.getFloatValue().doubleValue());
                        }
                        statement.setString(7, item.getFloatValueRaw());
                        statement.setString(8, item.getImageUrl());
                        statement.setString(9, item.getWearName());
                        statement.setString(10, item.getCollection());
                        statement.setString(11, item.getRarity());
                        statement.setString(12, item.getQualityLabel());
                        statement.setBoolean(13, item.isTradable());
                        statement.setString(14, writeRaw(item.getRaw()));
                    }

                    public int getBatchSize() {
                        return items.size();
                    }
                }
            );
        }

        InventorySnapshotRecord record = new InventorySnapshotRecord();
        record.setId(snapshotId);
        record.setGame(game);
        record.setItemCount(items.size());
        record.setFingerprint(fingerprint);
        record.setCreatedAt(now);
        record.setLastSeenAt(now);
        return record;
    }

    public void touch(long snapshotId) {
        jdbcTemplate.update("UPDATE inventory_snapshot SET last_seen_at = ? WHERE id = ?", Long.valueOf(System.currentTimeMillis()), Long.valueOf(snapshotId));
    }

    private Map<String, Object> readRaw(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(rawJson, MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read persisted raw inventory item.", ex);
        }
    }

    private String writeRaw(Map<String, Object> raw) {
        try {
            return objectMapper.writeValueAsString(raw == null ? Collections.emptyMap() : raw);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist raw inventory item.", ex);
        }
    }
}
