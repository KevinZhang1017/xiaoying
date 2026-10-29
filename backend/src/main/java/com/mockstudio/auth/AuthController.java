package com.mockstudio.auth;

import com.mockstudio.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AppProperties props;

    public AuthController(AuthService authService, AppProperties props) {
        this.authService = authService;
        this.props = props;
    }

    public record RegisterRequest(
            @NotBlank(message = "请输入邮箱") @Email(message = "邮箱格式不正确") @Size(max = 191) String email,
            @NotBlank(message = "请输入密码") @Size(min = 6, max = 72, message = "密码长度需为 6-72 位") String password,
            @Size(max = 64, message = "昵称最多 64 个字符") String displayName) {
    }

    public record LoginRequest(@NotBlank(message = "请输入邮箱") String email,
                               @NotBlank(message = "请输入密码") String password) {
    }

    public record UserDto(long id, String email, String displayName, Instant createdAt) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getCreatedAt());
        }
    }

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest req) {
        User user = authService.register(req.email(), req.password(), req.displayName());
        String token = authService.createSession(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, props.auth().sessionTtl()).toString())
                .body(UserDto.of(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest req) {
        User user = authService.authenticate(req.email(), req.password());
        String token = authService.createSession(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(token, props.auth().sessionTtl()).toString())
                .body(UserDto.of(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(AuthInterceptor.readToken(request, props.auth().cookieName()));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    public UserDto me(AuthUser user) {
        return new UserDto(user.id(), user.email(), user.displayName(), null);
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        return ResponseCookie.from(props.auth().cookieName(), value)
                .httpOnly(true)
                .secure(props.auth().cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
