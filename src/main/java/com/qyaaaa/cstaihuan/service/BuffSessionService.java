package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.config.BuffSessionProperties;
import com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.BuffAccountProfile;
import com.qyaaaa.cstaihuan.model.BuffSessionRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BuffSessionService {
    private final BuffProperties buffProperties;
    private final BuffSessionProperties buffSessionProperties;
    private final BuffApiClient buffApiClient;
    private final BuffAccountService buffAccountService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BuffSessionService(BuffProperties buffProperties, BuffSessionProperties buffSessionProperties, BuffApiClient buffApiClient, BuffAccountService buffAccountService, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.buffProperties = buffProperties;
        this.buffSessionProperties = buffSessionProperties;
        this.buffApiClient = buffApiClient;
        this.buffAccountService = buffAccountService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public BuffSessionStatusResponse getStatus() throws IOException {
        return getStatus(buffAccountService.resolveDefaultAccountId());
    }

    public BuffSessionStatusResponse getStatus(long accountId) throws IOException {
        buffAccountService.requireAccount(accountId);
        Optional<BuffSessionRecord> record = loadRecord(accountId);
        if (!record.isPresent()) {
            return new BuffSessionStatusResponse(false, false, null, null, null, null, "尚未保存 BUFF 会话。");
        }

        BuffSessionRecord current = record.get();
        return new BuffSessionStatusResponse(
            true,
            false,
            current.getSource(),
            maskCookie(current.getCookie()),
            current.getUpdatedAt(),
            current.getLastValidatedAt(),
            "已保存会话，建议在抓取前先校验。"
        );
    }

    public BuffSessionStatusResponse importSession(BuffSessionImportRequest request) throws IOException {
        return importSession(buffAccountService.resolveDefaultAccountId(), request);
    }

    public BuffSessionStatusResponse importSession(long accountId, BuffSessionImportRequest request) throws IOException {
        buffAccountService.requireAccount(accountId);
        if (!StringUtils.hasText(request.getCookie())) {
            throw new IllegalArgumentException(ErrorMessages.COOKIE_REQUIRED);
        }

        BuffSessionRecord record = new BuffSessionRecord();
        record.setCookie(request.getCookie().trim());
        record.setSource(StringUtils.hasText(request.getSource()) ? request.getSource().trim() : "frontend");
        record.setUpdatedAt(now());
        saveRecord(accountId, record);
        buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "UNKNOWN", null);
        return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), null, "会话已保存到后端。");
    }

    public BuffSessionStatusResponse validateSession() throws IOException {
        return validateSession(buffAccountService.resolveDefaultAccountId());
    }

    public BuffSessionStatusResponse validateSession(long accountId) throws IOException {
        BuffSessionRecord record = requireRecord(accountId);
        try {
            BuffAccountProfile profile = buffApiClient.fetchAccountProfileFromInventory(buffProperties.getBaseUrl(), record.getCookie(), buffProperties.getGame());
            if (profile == null) {
                buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "INVALID", record.getLastValidatedAt());
                return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), ErrorMessages.BUFF_SESSION_VALIDATE_FAILED);
            }
            record.setLastValidatedAt(now());
            saveRecord(accountId, record);
            buffAccountService.updateImportedIdentity(accountId, profile.getNickname(), profile.getBuffUserId());
            buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "VALID", record.getLastValidatedAt());
            return new BuffSessionStatusResponse(true, true, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), "BUFF 会话有效。");
        } catch (BuffRateLimitException ex) {
            // 限流是临时故障，不能据此判定会话失效，否则会把正常账号误标掉线。保持原状态，等下次再校验。
            return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), ErrorMessages.BUFF_RATE_LIMIT);
        } catch (RuntimeException ex) {
            buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "INVALID", record.getLastValidatedAt());
            return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), ErrorMessages.BUFF_SESSION_VALIDATE_FAILED);
        }
    }

    public void clearSession() throws IOException {
        clearSession(buffAccountService.resolveDefaultAccountId());
    }

    public void clearSession(long accountId) throws IOException {
        buffAccountService.requireAccount(accountId);
        jdbcTemplate.update("DELETE FROM buff_session WHERE account_id = ?", Long.valueOf(accountId));
        buffAccountService.updateSessionSummary(accountId, null, "UNKNOWN", null);
    }

    public String resolveCookie(String requestCookie) throws IOException {
        return resolveCookie(buffAccountService.resolveDefaultAccountId(), requestCookie);
    }

    public String resolveCookie(long accountId, String requestCookie) throws IOException {
        buffAccountService.requireAccount(accountId);
        if (StringUtils.hasText(requestCookie)) {
            return requestCookie.trim();
        }
        Optional<BuffSessionRecord> record = loadRecord(accountId);
        if (record.isPresent() && StringUtils.hasText(record.get().getCookie())) {
            return record.get().getCookie().trim();
        }
        throw new IllegalArgumentException(ErrorMessages.BUFF_COOKIE_MISSING);
    }

    private Optional<BuffSessionRecord> loadRecord(long accountId) throws IOException {
        List<BuffSessionRecord> rows = jdbcTemplate.query(
            "SELECT cookie_text, source, updated_at, last_validated_at FROM buff_session WHERE account_id = ?",
            (rs, rowNum) -> {
                BuffSessionRecord record = new BuffSessionRecord();
                record.setCookie(rs.getString("cookie_text"));
                record.setSource(rs.getString("source"));
                record.setUpdatedAt(rs.getString("updated_at"));
                record.setLastValidatedAt(rs.getString("last_validated_at"));
                return record;
            },
            Long.valueOf(accountId)
        );
        if (!rows.isEmpty()) {
            return Optional.of(rows.get(0));
        }
        return migrateLegacyDefaultSession(accountId);
    }

    private BuffSessionRecord requireRecord(long accountId) throws IOException {
        Optional<BuffSessionRecord> record = loadRecord(accountId);
        if (!record.isPresent()) {
            throw new IllegalArgumentException(ErrorMessages.NO_BUFF_SESSION_SAVED);
        }
        return record.get();
    }

    private void saveRecord(long accountId, BuffSessionRecord record) throws IOException {
        String createdAt = record.getUpdatedAt() == null ? now() : record.getUpdatedAt();
        jdbcTemplate.update(
            "INSERT INTO buff_session (account_id, cookie_text, source, created_at, updated_at, last_validated_at) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE cookie_text = VALUES(cookie_text), source = VALUES(source), updated_at = VALUES(updated_at), last_validated_at = VALUES(last_validated_at)",
            Long.valueOf(accountId),
            record.getCookie(),
            record.getSource(),
            createdAt,
            record.getUpdatedAt(),
            record.getLastValidatedAt()
        );
    }

    private Optional<BuffSessionRecord> migrateLegacyDefaultSession(long accountId) throws IOException {
        if (accountId != buffAccountService.resolveDefaultAccountId()) {
            return Optional.empty();
        }
        Path path = Paths.get(buffSessionProperties.getStoragePath());
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        BuffSessionRecord record = objectMapper.readValue(path.toFile(), BuffSessionRecord.class);
        if (!StringUtils.hasText(record.getCookie())) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(record.getUpdatedAt())) {
            record.setUpdatedAt(now());
        }
        saveRecord(accountId, record);
        buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "UNKNOWN", record.getLastValidatedAt());
        return Optional.of(record);
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }

    private String maskCookie(String cookie) {
        if (!StringUtils.hasText(cookie)) {
            return null;
        }
        String trimmed = cookie.trim();
        if (trimmed.length() <= 16) {
            return "****";
        }
        return trimmed.substring(0, 8) + "..." + trimmed.substring(trimmed.length() - 8);
    }
}
