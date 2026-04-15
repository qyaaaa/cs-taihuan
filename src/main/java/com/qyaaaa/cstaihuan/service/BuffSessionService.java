package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.config.BuffSessionProperties;
import com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.model.BuffSessionRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BuffSessionService {
    private final BuffSessionProperties buffSessionProperties;
    private final BuffProperties buffProperties;
    private final BuffApiClient buffApiClient;
    private final ObjectMapper objectMapper;

    public BuffSessionService(BuffSessionProperties buffSessionProperties, BuffProperties buffProperties, BuffApiClient buffApiClient, ObjectMapper objectMapper) {
        this.buffSessionProperties = buffSessionProperties;
        this.buffProperties = buffProperties;
        this.buffApiClient = buffApiClient;
        this.objectMapper = objectMapper;
    }

    public BuffSessionStatusResponse getStatus() throws IOException {
        Optional<BuffSessionRecord> record = loadRecord();
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
        if (!StringUtils.hasText(request.getCookie())) {
            throw new IllegalArgumentException("cookie is required.");
        }

        BuffSessionRecord record = new BuffSessionRecord();
        record.setCookie(request.getCookie().trim());
        record.setSource(StringUtils.hasText(request.getSource()) ? request.getSource().trim() : "frontend");
        record.setUpdatedAt(now());
        saveRecord(record);
        return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), null, "会话已保存到后端。");
    }

    public BuffSessionStatusResponse validateSession() throws IOException {
        BuffSessionRecord record = requireRecord();
        try {
            buffApiClient.fetchInventory(buffProperties.getBaseUrl(), record.getCookie(), buffProperties.getGame(), 1, Integer.valueOf(1));
            record.setLastValidatedAt(now());
            saveRecord(record);
            return new BuffSessionStatusResponse(true, true, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), "BUFF 会话有效。");
        } catch (RuntimeException ex) {
            return new BuffSessionStatusResponse(true, false, record.getSource(), maskCookie(record.getCookie()), record.getUpdatedAt(), record.getLastValidatedAt(), "会话校验失败，请重新导入最新 Cookie。");
        }
    }

    public void clearSession() throws IOException {
        Files.deleteIfExists(storagePath());
    }

    public String resolveCookie(String requestCookie) throws IOException {
        if (StringUtils.hasText(requestCookie)) {
            return requestCookie.trim();
        }
        Optional<BuffSessionRecord> record = loadRecord();
        if (record.isPresent() && StringUtils.hasText(record.get().getCookie())) {
            return record.get().getCookie().trim();
        }
        throw new IllegalArgumentException("BUFF cookie is missing. Please import a session from the frontend first.");
    }

    private Optional<BuffSessionRecord> loadRecord() throws IOException {
        Path path = storagePath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(path.toFile(), BuffSessionRecord.class));
    }

    private BuffSessionRecord requireRecord() throws IOException {
        Optional<BuffSessionRecord> record = loadRecord();
        if (!record.isPresent()) {
            throw new IllegalArgumentException("No BUFF session has been saved.");
        }
        return record.get();
    }

    private void saveRecord(BuffSessionRecord record) throws IOException {
        Path path = storagePath();
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), record);
    }

    private Path storagePath() {
        return Paths.get(buffSessionProperties.getStoragePath());
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
