package com.mockstudio.config;

import com.mockstudio.auth.AuthInterceptor;
import com.mockstudio.auth.AuthUserArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AuthUserArgumentResolver authUserArgumentResolver;

    public WebConfig(AuthInterceptor authInterceptor, AuthUserArgumentResolver authUserArgumentResolver) {
        this.authInterceptor = authInterceptor;
        this.authUserArgumentResolver = authUserArgumentResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/categories",
                        "/api/templates",
                        "/api/templates/**",
                        "/api/dev/**"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
    }
}
