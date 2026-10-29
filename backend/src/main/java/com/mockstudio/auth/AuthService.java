package com.mockstudio.auth;

import com.mockstudio.common.ApiException;
import com.mockstudio.common.Times;
import com.mockstudio.config.AppProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final AppProperties props;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserMapper userMapper, AppProperties props, Clock clock) {
        this.userMapper = userMapper;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String password, String displayName) {
        String normalized = normalizeEmail(email);
        if (userMapper.findByEmail(normalized) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "该邮箱已注册");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank()
                ? normalized.substring(0, normalized.indexOf('@')) : displayName.trim());
        user.setCreatedAt(Times.now(clock));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "该邮箱已注册");
        }
        return user;
    }

    public User authenticate(String email, String password) {
        User user = userMapper.findByEmail(normalizeEmail(email));
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码错误");
        }
        return user;
    }

    /** 创建会话，返回原始 token（只写入 Cookie，数据库只保存哈希）。 */
    public String createSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Times.now(clock);
        userMapper.insertSession(hash(token), user.getId(), now, now.plus(props.auth().sessionTtl()));
        return token;
    }

    public Optional<User> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.findBySession(hash(token), Times.now(clock)));
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            userMapper.deleteSession(hash(token));
        }
    }

    public boolean exists(String email) {
        return userMapper.findByEmail(normalizeEmail(email)) != null;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
