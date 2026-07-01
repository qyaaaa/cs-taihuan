package com.qyaaaa.cstaihuan.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
import com.qyaaaa.cstaihuan.dto.QrLoginStartResponse;
import com.qyaaaa.cstaihuan.dto.QrLoginStatusResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BuffQrLoginService {
    private static final Logger log = LoggerFactory.getLogger(BuffQrLoginService.class);
    private static final long SESSION_TIMEOUT_MILLIS = 5L * 60L * 1000L; // 5 分钟
    private static final long QR_RENDER_TIMEOUT_MILLIS = 22000L; // 等二维码图片出现的最长时间（BUFF 渲染约 6~22 秒，发现即返回）
    private static final long QR_POLL_INTERVAL_MILLIS = 250L; // 等待二维码时的轮询间隔
    private static final String BUFF_LOGIN_URL = "https://buff.163.com/account/login";
    // 预热二维码只在该新鲜窗口内发放，避免把 BUFF 已过期二维码给前端。
    private static final long WARM_MAX_AGE_MILLIS = 120_000L;
    // 后台刷新器重建预热会话的间隔，确保会话过期前被替换。
    private static final long WARM_REFRESH_INTERVAL_MILLIS = 60_000L;

    private final BuffSessionService buffSessionService;
    private final BuffAccountService buffAccountService;
    private final Map<String, QrLoginSession> sessions = new ConcurrentHashMap<>();

    // Playwright Java 不是线程安全的：整个浏览器共用一条驱动连接，因此预热线程和请求线程必须串行调用。
    private final ReentrantLock pwLock = new ReentrantLock(true);
    // 单个预渲染会话，用于下一次 start 请求时立即发放。
    private final AtomicReference<QrLoginSession> warmSession = new AtomicReference<>();
    private ScheduledExecutorService warmExecutor;
    private volatile boolean shuttingDown = false;

    private Playwright playwright;
    private Browser browser;

    public BuffQrLoginService(BuffSessionService buffSessionService, BuffAccountService buffAccountService) {
        this.buffSessionService = buffSessionService;
        this.buffAccountService = buffAccountService;
    }

    @PostConstruct
    public void init() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList(
                    "--no-sandbox",
                    "--disable-setuid-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu"
                )));
            log.info("Playwright browser launched successfully");
            // 单线程保证预热构建之间串行，pwLock 保证预热线程与请求线程串行；立即预渲染一张二维码并保持新鲜。
            warmExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "qr-warm-pool");
                t.setDaemon(true);
                return t;
            });
            warmExecutor.execute(this::ensureWarmFresh);
            warmExecutor.scheduleWithFixedDelay(this::ensureWarmFresh,
                WARM_REFRESH_INTERVAL_MILLIS, WARM_REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to launch Playwright browser: {}", e.getMessage(), e);
            playwright = null;
            browser = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        if (warmExecutor != null) {
            warmExecutor.shutdownNow();
            try {
                warmExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        QrLoginSession warm = warmSession.getAndSet(null);
        if (warm != null) {
            safeCloseContext(warm);
        }
        cleanupAllSessions();
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("Error closing playwright: {}", e.getMessage());
            }
        }
    }

    public boolean isAvailable() {
        return browser != null && playwright != null;
    }

    public QrLoginStartResponse startLogin() throws IOException {
        return startLoginInternal(null);
    }

    public QrLoginStartResponse startLogin(long accountId) throws IOException {
        return startLoginInternal(accountId);
    }

    private QrLoginStartResponse startLoginInternal(Long accountId) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("浏览器环境未就绪，请检查服务器 Chromium 安装。");
        }

        // 清理该账号已有的登录会话。
        cleanupAccountSession(accountId);
        cleanupExpiredSessions();

        try {
            // 快路径：如果已有新鲜的预渲染二维码，直接发给前端，再后台补充下一张。
            QrLoginSession session = takeWarmSession();
            if (session != null) {
                long now = Instant.now().toEpochMilli();
                session.accountId = accountId;
                session.createdAt = now;
                session.expiresAt = now + SESSION_TIMEOUT_MILLIS;
                log.info("QR login session={} served from warm pool, accountId={}", session.sessionId, accountId);
            } else {
                // 慢路径：没有可用预热会话时同步创建。captureQrCode 已内置二维码区域截图兜底，不会展示整页截图。
                session = buildSession(accountId, UUID.randomUUID().toString());
            }

            sessions.put(session.sessionId, session);
            log.info("QR login session={} created, status={}, accountId={}, expiresAt={}",
                session.sessionId, session.status, session.accountId, Long.valueOf(session.expiresAt));

            // 为下一次请求重新补充预热会话。
            scheduleWarm();

            return new QrLoginStartResponse(
                session.sessionId,
                session.qrcodeBase64,
                session.status,
                "SUCCESS".equals(session.status) ? "已检测到登录状态，正在保存会话。" : "请使用网易 BUFF App 扫描二维码登录。",
                session.expiresAt,
                session.accountId
            );
        } catch (Exception e) {
            log.error("Failed to start QR login: {}", e.getMessage(), e);
            scheduleWarm();
            // 返回清晰可处理的错误信息（转成 400），避免暴露原始 500 堆栈。
            throw new IllegalArgumentException("BUFF 登录页暂时无法访问（可能被风控限流），请稍候重试，或改用「手动导入」。");
        }
    }

    // 在 Playwright 锁内导航到 BUFF 登录页；遇到临时连接失败时重试一次（BUFF 高负载下偶尔会重置连接）。
    private void navigateWithRetry(Page page, String sessionId) {
        try {
            page.navigate(BUFF_LOGIN_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.COMMIT)
                .setTimeout(15000));
        } catch (RuntimeException first) {
            log.warn("QR login session={} first navigation failed ({}), retrying once...",
                sessionId, first.getMessage());
            try {
                Thread.sleep(800);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            page.navigate(BUFF_LOGIN_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.COMMIT)
                .setTimeout(15000));
        }
    }

    // 原子取出预热会话：只有仍然新鲜、可用且停留在登录页时才返回。
    private QrLoginSession takeWarmSession() {
        QrLoginSession warm = warmSession.getAndSet(null);
        if (warm == null) {
            return null;
        }
        long now = Instant.now().toEpochMilli();
        boolean tooOld = (now - warm.createdAt) > WARM_MAX_AGE_MILLIS;
        boolean unusable = warm.qrcodeBase64 == null || isPageClosedLocked(warm.page);
        if (tooOld || unusable) {
            safeCloseContext(warm);
            return null;
        }
        return warm;
    }

    /**
     * 创建一个已捕获二维码的新登录会话；同步慢路径和预热会话复用这段逻辑。
     * 所有 Playwright 调用都必须在 {@link #pwLock} 保护下执行。
     */
    private QrLoginSession buildSession(Long accountId, String sessionId) {
        BrowserContext context = null;
        Page page;
        String status = "PENDING";
        Long resolvedAccountId = accountId;
        String cookieStrForSuccess = null;

        pwLock.lock();
        try {
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("zh-CN"));
            page = context.newPage();

            // 文档响应提交（COMMIT）后立即返回，不等 DOMContentLoaded/NETWORKIDLE；
            // BUFF 的重脚本会让那些状态多等约 15 秒。BUFF 偶发断连时重试一次，避免短暂波动直接暴露给用户。
            log.info("Building QR login session={}, navigating to BUFF login page...", sessionId);
            navigateWithRetry(page, sessionId);

            String currentUrl = page.url().toLowerCase();
            // 如果因已有 cookie 直接处于登录态，BUFF 会跳出登录页；锁内只做检测，锁外再持久化 cookie。
            if (!currentUrl.contains("/account/login")) {
                List<Cookie> cookies = context.cookies();
                if (hasLoginCookie(cookies)) {
                    cookieStrForSuccess = buildCookieString(cookies);
                    status = "SUCCESS";
                }
            }
        } catch (Exception e) {
            if (context != null) {
                try { context.close(); } catch (Exception ex) { /* 忽略关闭失败 */ }
            }
            pwLock.unlock();
            throw new IllegalStateException("启动扫码登录失败: " + e.getMessage(), e);
        }
        pwLock.unlock();

        // captureQrCode 每次轮询内部自行加锁，等待期间不会阻塞状态轮询。
        String qrcodeBase64 = null;
        if (!"SUCCESS".equals(status)) {
            qrcodeBase64 = captureQrCode(page, sessionId);
        }

        long now = Instant.now().toEpochMilli();
        QrLoginSession session = new QrLoginSession();
        session.sessionId = sessionId;
        session.browserContext = context;
        session.page = page;
        session.accountId = resolvedAccountId;
        session.status = status;
        session.qrcodeBase64 = qrcodeBase64;
        session.createdAt = now;
        session.expiresAt = now + SESSION_TIMEOUT_MILLIS;

        // 罕见的“已登录”分支需要写数据库，放在 Playwright 锁外执行。
        if ("SUCCESS".equals(status) && cookieStrForSuccess != null) {
            try {
                session.accountId = Long.valueOf(resolveOrCreateAccountId(accountId));
                importCookies(cookieStrForSuccess, session.accountId);
            } catch (Exception e) {
                log.warn("QR login session={} pre-authenticated cookie import failed: {}", sessionId, e.getMessage());
            }
        }
        return session;
    }

    // 提交预热会话刷新任务到单线程；已有新鲜预热会话时不做事。
    private void scheduleWarm() {
        if (shuttingDown || warmExecutor == null || !isAvailable()) {
            return;
        }
        try {
            warmExecutor.execute(this::ensureWarmFresh);
        } catch (Exception e) {
            // 线程池正在关闭，忽略即可。
        }
    }

    // 只在预热单线程中运行：缺少预热会话或已过期时重建。
    private void ensureWarmFresh() {
        if (shuttingDown || !isAvailable()) {
            return;
        }
        try {
            QrLoginSession cur = warmSession.get();
            long now = Instant.now().toEpochMilli();
            boolean fresh = cur != null
                && cur.qrcodeBase64 != null
                && (now - cur.createdAt) < WARM_MAX_AGE_MILLIS
                && !isPageClosedLocked(cur.page);
            if (fresh) {
                return;
            }
            QrLoginSession old = warmSession.getAndSet(null);
            if (old != null) {
                safeCloseContext(old);
            }
            QrLoginSession built = buildSession(null, UUID.randomUUID().toString());
            if (shuttingDown || built.qrcodeBase64 == null) {
                safeCloseContext(built);
                return;
            }
            warmSession.set(built);
            log.info("Warm QR session={} ready", built.sessionId);
        } catch (Exception e) {
            log.warn("Failed to refresh warm QR session: {}", e.getMessage());
        }
    }

    public QrLoginStatusResponse getStatus(String sessionId) throws IOException {
        QrLoginSession session = sessions.get(sessionId);
        if (session == null) {
            return new QrLoginStatusResponse(sessionId, "EXPIRED", "登录会话已过期或不存在。", false, false);
        }

        // 检查二维码是否超时。
        if (Instant.now().toEpochMilli() > session.expiresAt) {
            cleanupSession(sessionId);
            return new QrLoginStatusResponse(sessionId, "EXPIRED", "二维码已过期，请重新获取。", false, false);
        }

        if ("SUCCESS".equals(session.status)) {
            // 已经登录成功。
            return new QrLoginStatusResponse(sessionId, "SUCCESS", "登录成功。", true, true, session.accountId);
        }

        // 在一个锁定区块中读取页面状态；后续数据库操作放到锁外。
        boolean onLoginPage;
        List<Cookie> cookies = null;
        boolean qrInvalid = false;
        boolean scanFinished = false;
        pwLock.lock();
        try {
            Page page = session.page;
            if (page == null || page.isClosed()) {
                pwLock.unlock();
                cleanupSession(sessionId);
                return new QrLoginStatusResponse(sessionId, "FAILED", "浏览器页面已关闭。", false, false);
            }
            String currentUrl = page.url().toLowerCase();
            onLoginPage = currentUrl.contains("/account/login");
            if (!onLoginPage) {
                cookies = session.browserContext.cookies();
            } else {
                qrInvalid = isElementVisibleLocked(page, "#qrcode_invalid");
                scanFinished = isElementVisibleLocked(page, "#qrcode_scan_finish");
            }
        } catch (Exception e) {
            pwLock.unlock();
            log.error("Error polling QR login session={}: {}", sessionId, e.getMessage(), e);
            return new QrLoginStatusResponse(sessionId, "PENDING", "轮询中...", false, false);
        }
        pwLock.unlock();

        // 登录完成后，BUFF 会在扫码确认后跳出 /account/login。
        if (!onLoginPage) {
            if (cookies == null || !hasLoginCookie(cookies)) {
                // 已跳转但 session cookie 尚未出现，再等一轮。
                return new QrLoginStatusResponse(sessionId, session.status, "登录跳转中，请稍候...", false, false);
            }
            log.info("QR login session={} left login page, extracting cookies...", sessionId);
            long targetAccountId = resolveOrCreateAccountId(session.accountId);
            session.accountId = Long.valueOf(targetAccountId);
            importCookies(buildCookieString(cookies), session.accountId);
            session.status = "SUCCESS";
            log.info("QR login session={} succeeded, cookies saved to account={}", sessionId, Long.valueOf(targetAccountId));
            safeCloseContext(session);
            return new QrLoginStatusResponse(sessionId, "SUCCESS", "登录成功，会话已保存。", true, true, Long.valueOf(targetAccountId));
        }

        if (qrInvalid) {
            session.status = "EXPIRED";
            return new QrLoginStatusResponse(sessionId, "EXPIRED", "二维码已过期，请重新获取。", false, false);
        }
        if (scanFinished) {
            if (!"CONFIRMED".equals(session.status)) {
                session.status = "CONFIRMED";
                log.info("QR login session={} status updated to CONFIRMED (scan finished)", sessionId);
            }
            return new QrLoginStatusResponse(sessionId, "CONFIRMED", "已扫码，请在网易 BUFF App 中确认登录。", false, false);
        }
        return new QrLoginStatusResponse(sessionId, session.status, "等待扫码中，请使用网易 BUFF App 扫描二维码。", false, false);
    }

    public void cancelLogin(String sessionId) {
        cleanupSession(sessionId);
        log.info("QR login session={} cancelled", sessionId);
    }

    // BUFF 登录二维码渲染为 #qr_code_box 内 src 为 data:image base64 的 img。
    private static final String[] QR_SELECTORS = {
        "#qr_code_box img",
        "#login_qr_code_content img",
        ".login-qrcode .qrcode-area img",
        ".login-qrcode img",
    };

    // 返回 BUFF 登录二维码的 base64 内容（不含 data URL 前缀）。每次尝试时加 Playwright 锁，
    // 休眠等待放在锁外，因此状态轮询可以穿插执行。
    private String captureQrCode(Page page, String sessionId) {
        long deadline = Instant.now().toEpochMilli() + QR_RENDER_TIMEOUT_MILLIS;
        while (Instant.now().toEpochMilli() < deadline) {
            String qr = tryReadQrCode(page);
            if (qr != null) {
                log.info("QR login session={}, QR data URL captured", sessionId);
                return qr;
            }
            try {
                Thread.sleep(QR_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 清晰的 data:image 没及时出现时，只截图二维码区域而不是整页，前端仍只展示二维码（图片会略大）。
        String boxShot = captureQrBoxScreenshotLocked(page);
        if (boxShot != null) {
            log.info("QR login session={}, QR captured via box element screenshot (data URL timed out)", sessionId);
            return boxShot;
        }
        log.info("QR login session={}, QR not found after waiting", sessionId);
        return null;
    }

    // 在锁内尝试从已知选择器读取二维码 data URL；未就绪时返回 null。
    private String tryReadQrCode(Page page) {
        pwLock.lock();
        try {
            for (String selector : QR_SELECTORS) {
                try {
                    ElementHandle qrElement = page.querySelector(selector);
                    if (qrElement != null && qrElement.isVisible()) {
                        String src = qrElement.getAttribute("src");
                        if (src != null && src.startsWith("data:image")) {
                            int comma = src.indexOf(',');
                            if (comma > 0 && comma < src.length() - 1) {
                                return src.substring(comma + 1);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("QR selector {} failed: {}", selector, e.getMessage());
                }
            }
            return null;
        } finally {
            pwLock.unlock();
        }
    }

    private boolean isElementVisibleLocked(Page page, String selector) {
        pwLock.lock();
        try {
            ElementHandle el = page.querySelector(selector);
            return el != null && el.isVisible();
        } catch (Exception e) {
            return false;
        } finally {
            pwLock.unlock();
        }
    }

    private boolean isPageClosedLocked(Page page) {
        if (page == null) {
            return true;
        }
        pwLock.lock();
        try {
            return page.isClosed();
        } catch (Exception e) {
            return true;
        } finally {
            pwLock.unlock();
        }
    }

    // BUFF 只会在登录成功后设置非空 session cookie。
    private boolean hasLoginCookie(List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            if ("session".equalsIgnoreCase(cookie.name) && cookie.value != null && !cookie.value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void importCookies(String cookieStr, Long accountId) throws IOException {
        com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest importReq =
            new com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest();
        importReq.setCookie(cookieStr);
        importReq.setSource("qrcode");
        buffSessionService.importSession(
            accountId != null ? accountId : buffAccountService.resolveDefaultAccountId(),
            importReq
        );
    }

    // 返回传入账号 id；如果二维码会话尚未绑定账号，则创建新本地账号（“新增 → 扫码成功后才建账号”流程）。
    private long resolveOrCreateAccountId(Long accountId) {
        if (accountId != null) {
            return accountId.longValue();
        }
        com.qyaaaa.cstaihuan.dto.BuffAccountRequest request = new com.qyaaaa.cstaihuan.dto.BuffAccountRequest();
        request.setNickname("账号 " + (buffAccountService.listAccounts().size() + 1));
        long newId = buffAccountService.createAccount(request).getId();
        log.info("Created new account={} for pending QR login", newId);
        return newId;
    }

    // 二维码区域截图兜底用的候选选择器，即承载二维码的白色登录卡片。
    private static final String[] QR_BOX_SELECTORS = {
        "#qr_code_box",
        "#login_qr_code_content",
        ".login-qrcode",
    };

    // 只截取二维码卡片元素（不是整页），返回 base64 PNG。
    private String captureQrBoxScreenshotLocked(Page page) {
        pwLock.lock();
        try {
            for (String selector : QR_BOX_SELECTORS) {
                ElementHandle box = page.querySelector(selector);
                if (box != null && box.isVisible()) {
                    byte[] png = box.screenshot(new ElementHandle.ScreenshotOptions().setType(ScreenshotType.PNG));
                    return Base64.getEncoder().encodeToString(png);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to capture QR box screenshot: {}", e.getMessage());
            return null;
        } finally {
            pwLock.unlock();
        }
    }

    private String buildCookieString(List<Cookie> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : cookies) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(cookie.name).append('=').append(cookie.value);
        }
        return sb.toString();
    }

    private void cleanupAccountSession(Long accountId) {
        if (accountId == null) {
            return;
        }
        Iterator<Map.Entry<String, QrLoginSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, QrLoginSession> entry = it.next();
            QrLoginSession session = entry.getValue();
            if (accountId.equals(session.accountId)) {
                safeCloseContext(session);
                it.remove();
                log.info("Cleaned up existing QR login session={} for account={}", session.sessionId, accountId);
            }
        }
    }

    private void cleanupExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        Iterator<Map.Entry<String, QrLoginSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, QrLoginSession> entry = it.next();
            QrLoginSession session = entry.getValue();
            if (now > session.expiresAt) {
                safeCloseContext(session);
                it.remove();
                log.info("Cleaned up expired QR login session={}", session.sessionId);
            }
        }
    }

    private void cleanupSession(String sessionId) {
        QrLoginSession session = sessions.remove(sessionId);
        if (session != null) {
            safeCloseContext(session);
        }
    }

    private void cleanupAllSessions() {
        for (QrLoginSession session : sessions.values()) {
            safeCloseContext(session);
        }
        sessions.clear();
    }

    private void safeCloseContext(QrLoginSession session) {
        pwLock.lock();
        try {
            if (session.page != null && !session.page.isClosed()) {
                session.page.close();
            }
        } catch (Exception e) {
            log.debug("Error closing page: {}", e.getMessage());
        } finally {
            pwLock.unlock();
        }
        pwLock.lock();
        try {
            if (session.browserContext != null) {
                session.browserContext.close();
            }
        } catch (Exception e) {
            log.debug("Error closing browser context: {}", e.getMessage());
        } finally {
            pwLock.unlock();
        }
    }

    private static class QrLoginSession {
        String sessionId;
        BrowserContext browserContext;
        Page page;
        Long accountId;
        String status;
        String qrcodeBase64;
        long createdAt;
        long expiresAt;
    }
}
