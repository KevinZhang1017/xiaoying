package com.mockstudio.auth;

/** 当前请求的已认证用户，由 {@link AuthInterceptor} 解析并注入到 Controller 参数。 */
public record AuthUser(long id, String email, String displayName) {

    public static AuthUser from(User u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getDisplayName());
    }
}
