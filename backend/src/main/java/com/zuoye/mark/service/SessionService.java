package com.zuoye.mark.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public static final String COOKIE_NAME = "mark_session";
    public static final String REQUEST_USER_ATTRIBUTE = "currentPlatformUser";
    private static final Duration SESSION_LIFETIME = Duration.ofHours(12);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;

    public SessionService(
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
    }

    public Optional<PlatformUser> current(HttpServletRequest request) {
        Object cached = request.getAttribute(REQUEST_USER_ATTRIBUTE);
        if (cached instanceof PlatformUser user) return Optional.of(user);
        String token = readCookie(request);
        if (token == null || token.isBlank()) return Optional.empty();
        List<PlatformUser> users = jdbcTemplate.query(
                """
                SELECT u.id, u.username, u.displayName, u.role, u.status,
                       u.studentNo, u.classId
                FROM auth_session s
                JOIN app_user u ON u.id = s.userId
                WHERE s.tokenHash = ?
                  AND s.revokedAt IS NULL
                  AND s.expiresAt > CURRENT_TIMESTAMP(3)
                  AND u.status = 'ACTIVE'
                LIMIT 1
                """,
                (row, index) -> new PlatformUser(
                        row.getInt("id"),
                        row.getString("username"),
                        row.getString("displayName"),
                        row.getString("role"),
                        row.getString("status"),
                        row.getString("studentNo"),
                        row.getObject("classId") == null
                                ? null
                                : row.getInt("classId")),
                hash(token));
        if (users.isEmpty()) return Optional.empty();
        PlatformUser user = users.get(0);
        request.setAttribute(REQUEST_USER_ATTRIBUTE, user);
        jdbcTemplate.update(
                "UPDATE auth_session SET lastSeenAt = CURRENT_TIMESTAMP(3) WHERE tokenHash = ?",
                hash(token));
        return Optional.of(user);
    }

    public PlatformUser required(HttpServletRequest request) {
        return current(request).orElseThrow(() -> new PlatformException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "登录状态已失效，请重新登录。"));
    }

    public PlatformUser required(
            HttpServletRequest request,
            String... allowedRoles) {
        PlatformUser user = required(request);
        for (String role : allowedRoles) {
            if (role.equals(user.role())) return user;
        }
        throw new PlatformException(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "当前角色无权执行此操作。");
    }

    public LoginSession login(
            String username,
            String password,
            String portal,
            HttpServletRequest request,
            HttpServletResponse response) {
        List<UserWithPassword> users = jdbcTemplate.query(
                """
                SELECT id, username, passwordHash, displayName, role, status,
                       studentNo, classId
                FROM app_user
                WHERE username = ?
                LIMIT 1
                """,
                (row, index) -> new UserWithPassword(
                        new PlatformUser(
                                row.getInt("id"),
                                row.getString("username"),
                                row.getString("displayName"),
                                row.getString("role"),
                                row.getString("status"),
                                row.getString("studentNo"),
                                row.getObject("classId") == null
                                        ? null
                                        : row.getInt("classId")),
                        row.getString("passwordHash")),
                username);
        if (users.isEmpty()
                || !passwordService.matches(password, users.get(0).passwordHash())) {
            throw new PlatformException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "账号或密码错误。");
        }
        PlatformUser user = users.get(0).user();
        if (!"ACTIVE".equals(user.status())) {
            throw new PlatformException(
                    HttpStatus.LOCKED,
                    "USER_DISABLED",
                    "账号已停用。");
        }
        if (portal != null
                && !portal.isBlank()
                && !portal.equals(user.role())) {
            throw new PlatformException(
                    HttpStatus.FORBIDDEN,
                    "PORTAL_MISMATCH",
                    "所选入口与账号角色不一致。");
        }
        String token = randomToken();
        Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
        jdbcTemplate.update(
                """
                INSERT INTO auth_session
                  (id, userId, tokenHash, expiresAt, userAgent, ipAddress)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                user.id(),
                hash(token),
                expiresAt.atZone(ZoneOffset.ofHours(8))
                        .toLocalDateTime(),
                truncate(request.getHeader("User-Agent"), 500),
                clientIp(request));
        jdbcTemplate.update(
                "UPDATE app_user SET lastLoginAt = CURRENT_TIMESTAMP(3) WHERE id = ?",
                user.id());
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) SESSION_LIFETIME.toSeconds());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        request.setAttribute(REQUEST_USER_ATTRIBUTE, user);
        String className = user.classId() == null
                ? null
                : jdbcTemplate.query(
                        "SELECT name FROM classroom WHERE id = ?",
                        rows -> rows.next() ? rows.getString(1) : null,
                        user.classId());
        LoginUser loginUser = new LoginUser(
                user.id(),
                user.username(),
                user.displayName(),
                "ACTIVE",
                List.of(user.role()),
                user.studentNo(),
                className,
                className == null ? List.of() : List.of(className));
        return new LoginSession(
                loginUser,
                "SESSION",
                null,
                expiresAt.toString());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request);
        if (token != null && !token.isBlank()) {
            jdbcTemplate.update(
                    """
                    UPDATE auth_session
                    SET revokedAt = CURRENT_TIMESTAMP(3)
                    WHERE tokenHash = ? AND revokedAt IS NULL
                    """,
                    hash(token));
        }
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        request.removeAttribute(REQUEST_USER_ATTRIBUTE);
    }

    public String cookieName() {
        return COOKIE_NAME;
    }

    private String readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("会话令牌处理失败。", error);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record PlatformUser(
            int id,
            String username,
            String displayName,
            String role,
            String status,
            String studentNo,
            Integer classId) {}

    public record LoginSession(
            LoginUser user,
            String authMode,
            String accessToken,
            String expiresAt) {}

    public record LoginUser(
            int id,
            String username,
            String displayName,
            String status,
            List<String> roles,
            String studentNo,
            String className,
            List<String> classNames) {}

    private record UserWithPassword(
            PlatformUser user,
            String passwordHash) {}
}
