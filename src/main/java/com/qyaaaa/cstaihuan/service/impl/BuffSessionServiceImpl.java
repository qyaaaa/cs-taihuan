package com.qyaaaa.cstaihuan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.config.BuffSessionProperties;
import com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.exception.BuffRateLimitException;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.mapper.BuffSessionMapper;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import com.qyaaaa.cstaihuan.model.BuffAccountProfile;
import com.qyaaaa.cstaihuan.model.BuffSession;
import com.qyaaaa.cstaihuan.model.BuffSessionRecord;
import com.qyaaaa.cstaihuan.service.BuffAccountService;
import com.qyaaaa.cstaihuan.service.BuffApiClient;
import com.qyaaaa.cstaihuan.service.BuffSessionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BuffSessionServiceImpl extends ServiceImpl<BuffSessionMapper, BuffSession> implements BuffSessionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BuffSessionServiceImpl.class);

    private final BuffProperties buffProperties;
    private final BuffSessionProperties buffSessionProperties;
    private final BuffApiClient buffApiClient;
    private final BuffAccountService buffAccountService;
    private final ObjectMapper objectMapper;

    public BuffSessionServiceImpl(BuffProperties buffProperties, BuffSessionProperties buffSessionProperties, BuffApiClient buffApiClient, BuffAccountService buffAccountService, ObjectMapper objectMapper) {
        this.buffProperties = buffProperties;
        this.buffSessionProperties = buffSessionProperties;
        this.buffApiClient = buffApiClient;
        this.buffAccountService = buffAccountService;
        this.objectMapper = objectMapper;
    }

    @Override
    public BuffSessionStatusResponse getStatus() throws IOException {
        return getStatus(buffAccountService.resolveDefaultAccountId());
    }

    @Override
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

    @Override
    public BuffSessionStatusResponse importSession(BuffSessionImportRequest request) throws IOException {
        return importSession(buffAccountService.resolveDefaultAccountId(), request);
    }

    @Override
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

    @Override
    public BuffSessionStatusResponse validateSession() throws IOException {
        return validateSession(buffAccountService.resolveDefaultAccountId());
    }

    /**
     * 校验 BUFF 会话并同步账号身份；如果同一个 BUFF 账号已存在，会把当前槽位合并到已有账号。
     */
    @Override
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

            // BUFF 账号身份以 buff_user_id 为准：若同一个 BUFF 账号已绑定到别的本地槽位，
            // 把刚验证好的新会话合并进那个已有账号，并删除当前重复槽位，避免同一账号占用多个槽位重复同步。
            Optional<BuffAccount> existing = buffAccountService.findOtherAccountByBuffUserId(profile.getBuffUserId(), accountId);
            if (existing.isPresent()) {
                long canonicalId = existing.get().getId();
                saveRecord(canonicalId, record);
                buffAccountService.updateImportedIdentity(canonicalId, profile.getNickname(), profile.getBuffUserId());
                buffAccountService.updateSessionSummary(canonicalId, maskCookie(record.getCookie()), "VALID", record.getLastValidatedAt());
                buffAccountService.deleteAccount(accountId);
                log.info("Merged duplicate BUFF account by buff_user_id, movedFrom={}, into={}, buffUserId={}",
                    Long.valueOf(accountId), Long.valueOf(canonicalId), profile.getBuffUserId());
                BuffSessionStatusResponse merged = new BuffSessionStatusResponse(true, true, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), "检测到该 BUFF 账号已存在，已合并到已有账号「" + existing.get().getNickname() + "」。");
                merged.setAccountId(Long.valueOf(canonicalId));
                return merged;
            }

            buffAccountService.updateImportedIdentity(accountId, profile.getNickname(), profile.getBuffUserId());
            buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "VALID", record.getLastValidatedAt());
            BuffSessionStatusResponse ok = new BuffSessionStatusResponse(true, true, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), "BUFF 会话有效。");
            ok.setAccountId(Long.valueOf(accountId));
            return ok;
        } catch (BuffRateLimitException ex) {
            // 限流是临时故障，不能据此判定会话失效，否则会把正常账号误标掉线。保持原状态，等下次再校验。
            return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), ErrorMessages.BUFF_RATE_LIMIT);
        } catch (RuntimeException ex) {
            buffAccountService.updateSessionSummary(accountId, maskCookie(record.getCookie()), "INVALID", record.getLastValidatedAt());
            return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), ErrorMessages.BUFF_SESSION_VALIDATE_FAILED);
        }
    }

    @Override
    public void clearSession() throws IOException {
        clearSession(buffAccountService.resolveDefaultAccountId());
    }

    @Override
    public void clearSession(long accountId) throws IOException {
        buffAccountService.requireAccount(accountId);
        baseMapper.deleteByAccountId(accountId);
        buffAccountService.updateSessionSummary(accountId, null, "UNKNOWN", null);
    }

    @Override
    public String resolveCookie(String requestCookie) throws IOException {
        return resolveCookie(buffAccountService.resolveDefaultAccountId(), requestCookie);
    }

    @Override
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

    /**
     * 优先读取数据库会话；默认账号缺失时兼容迁移旧版本地 JSON 会话文件。
     */
    private Optional<BuffSessionRecord> loadRecord(long accountId) throws IOException {
        BuffSession session = baseMapper.selectByAccountId(accountId);
        if (session != null) {
            return Optional.of(toRecord(session));
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

    /**
     * 会话表以 account_id 唯一索引做 upsert，避免同一账号重复导入时生成多条会话记录。
     */
    private void saveRecord(long accountId, BuffSessionRecord record) throws IOException {
        BuffSession session = new BuffSession();
        session.setAccountId(Long.valueOf(accountId));
        session.setCookieText(record.getCookie());
        session.setSource(record.getSource());
        session.setCreatedAt(record.getUpdatedAt() == null ? now() : record.getUpdatedAt());
        session.setUpdatedAt(record.getUpdatedAt());
        session.setLastValidatedAt(record.getLastValidatedAt());
        baseMapper.upsertSession(session);
    }

    /**
     * 旧版本只把 Cookie 存在本地文件；首次访问默认账号时迁移进数据库，保证历史用户平滑升级。
     */
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

    private BuffSessionRecord toRecord(BuffSession session) {
        BuffSessionRecord record = new BuffSessionRecord();
        record.setCookie(session.getCookieText());
        record.setSource(session.getSource());
        record.setUpdatedAt(session.getUpdatedAt());
        record.setLastValidatedAt(session.getLastValidatedAt());
        return record;
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
