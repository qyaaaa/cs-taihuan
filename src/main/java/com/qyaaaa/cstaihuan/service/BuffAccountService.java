package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.dto.BuffAccountRequest;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BuffAccountService {
    private static final String DEFAULT_NICKNAME = "默认账号";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_BUFF_USER_ID_LENGTH = 128;

    private final JdbcTemplate jdbcTemplate;

    public BuffAccountService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BuffAccount> listAccounts() {
        ensureDefaultAccount();
        return jdbcTemplate.query(
            "SELECT id, nickname, buff_user_id, masked_cookie, status, last_validated_at, created_at, updated_at FROM buff_account ORDER BY id ASC",
            (rs, rowNum) -> mapAccount(rs)
        );
    }

    public BuffAccount createAccount(BuffAccountRequest request) {
        long now = System.currentTimeMillis();
        String nickname = normalizeNickname(request == null ? null : request.getNickname());
        String buffUserId = normalizeNullable(request == null ? null : request.getBuffUserId());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO buff_account (nickname, buff_user_id, masked_cookie, status, last_validated_at, created_at, updated_at) VALUES (?, ?, NULL, ?, NULL, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, nickname);
            statement.setString(2, buffUserId);
            statement.setString(3, STATUS_UNKNOWN);
            statement.setLong(4, now);
            statement.setLong(5, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? listAccounts().get(listAccounts().size() - 1) : requireAccount(key.longValue());
    }

    public BuffAccount updateAccount(long accountId, BuffAccountRequest request) {
        requireAccount(accountId);
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
            "UPDATE buff_account SET nickname = ?, buff_user_id = ?, updated_at = ? WHERE id = ?",
            normalizeNickname(request == null ? null : request.getNickname()),
            normalizeNullable(request == null ? null : request.getBuffUserId()),
            Long.valueOf(now),
            Long.valueOf(accountId)
        );
        return requireAccount(accountId);
    }

    @Transactional
    public void deleteAccount(long accountId) {
        requireAccount(accountId);
        if (listAccounts().size() <= 1) {
            throw new IllegalArgumentException(ErrorMessages.BUFF_ACCOUNT_KEEP_ONE);
        }
        // These tables carry account_id but have no FK cascade; clean their rows explicitly
        // (children first) so deleting an account leaves no orphan data behind.
        Long id = Long.valueOf(accountId);
        jdbcTemplate.update("DELETE FROM catalog_sync_task WHERE account_id = ?", id);
        jdbcTemplate.update("DELETE FROM trade_up_next_tier_item WHERE account_id = ?", id);
        jdbcTemplate.update("DELETE FROM inventory_snapshot WHERE account_id = ?", id);
        // buff_session is removed automatically via ON DELETE CASCADE.
        jdbcTemplate.update("DELETE FROM buff_account WHERE id = ?", id);
    }

    public long resolveDefaultAccountId() {
        ensureDefaultAccount();
        Long id = jdbcTemplate.queryForObject("SELECT id FROM buff_account ORDER BY id ASC LIMIT 1", Long.class);
        return id == null ? 1L : id.longValue();
    }

    public BuffAccount requireAccount(long accountId) {
        Optional<BuffAccount> account = findById(accountId);
        if (!account.isPresent()) {
            throw new IllegalArgumentException(ErrorMessages.buffAccountNotFound(accountId));
        }
        return account.get();
    }

    public void updateSessionSummary(long accountId, String maskedCookie, String status, String lastValidatedAt) {
        requireAccount(accountId);
        jdbcTemplate.update(
            "UPDATE buff_account SET masked_cookie = ?, status = ?, last_validated_at = ?, updated_at = ? WHERE id = ?",
            maskedCookie,
            StringUtils.hasText(status) ? status : STATUS_UNKNOWN,
            lastValidatedAt,
            Long.valueOf(System.currentTimeMillis()),
            Long.valueOf(accountId)
        );
    }

    public void updateImportedIdentity(long accountId, String nickname, String buffUserId) {
        BuffAccount account = requireAccount(accountId);
        String normalizedNickname = normalizeNullable(nickname);
        String normalizedBuffUserId = normalizeNullable(buffUserId);
        jdbcTemplate.update(
            "UPDATE buff_account SET nickname = ?, buff_user_id = ?, updated_at = ? WHERE id = ?",
            normalizedNickname == null ? account.getNickname() : limit(normalizedNickname, MAX_NICKNAME_LENGTH),
            normalizedBuffUserId == null ? account.getBuffUserId() : limit(normalizedBuffUserId, MAX_BUFF_USER_ID_LENGTH),
            Long.valueOf(System.currentTimeMillis()),
            Long.valueOf(accountId)
        );
    }

    private Optional<BuffAccount> findById(long accountId) {
        List<BuffAccount> rows = jdbcTemplate.query(
            "SELECT id, nickname, buff_user_id, masked_cookie, status, last_validated_at, created_at, updated_at FROM buff_account WHERE id = ?",
            (rs, rowNum) -> mapAccount(rs),
            Long.valueOf(accountId)
        );
        return rows.isEmpty() ? Optional.<BuffAccount>empty() : Optional.of(rows.get(0));
    }

    private void ensureDefaultAccount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM buff_account", Integer.class);
        if (count != null && count.intValue() > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        jdbcTemplate.update(
            "INSERT INTO buff_account (nickname, buff_user_id, masked_cookie, status, last_validated_at, created_at, updated_at) VALUES (?, NULL, NULL, ?, NULL, ?, ?)",
            DEFAULT_NICKNAME,
            STATUS_UNKNOWN,
            Long.valueOf(now),
            Long.valueOf(now)
        );
    }

    private BuffAccount mapAccount(java.sql.ResultSet rs) throws java.sql.SQLException {
        BuffAccount account = new BuffAccount();
        account.setId(rs.getLong("id"));
        account.setNickname(rs.getString("nickname"));
        account.setBuffUserId(rs.getString("buff_user_id"));
        account.setMaskedCookie(rs.getString("masked_cookie"));
        account.setStatus(rs.getString("status"));
        account.setLastValidatedAt(rs.getString("last_validated_at"));
        account.setCreatedAt(rs.getLong("created_at"));
        account.setUpdatedAt(rs.getLong("updated_at"));
        return account;
    }

    private String normalizeNickname(String nickname) {
        return StringUtils.hasText(nickname) ? limit(nickname.trim(), MAX_NICKNAME_LENGTH) : DEFAULT_NICKNAME;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
