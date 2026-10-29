package com.mockstudio.auth;

import com.mockstudio.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * 创建两个演示账号，用于演示个人记录隔离。密码通过配置项提供，不写死在 SQL 中。
 */
@Component
public class DemoUserSeeder implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AuthService authService;
    private final AppProperties props;

    public DemoUserSeeder(AuthService authService, AppProperties props) {
        this.authService = authService;
        this.props = props;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!props.demoUsers().enabled()) {
            return;
        }
        seed("alice@demo.local", "Alice");
        seed("bob@demo.local", "Bob");
    }

    private void seed(String email, String name) {
        if (!authService.exists(email)) {
            authService.register(email, props.demoUsers().password(), name);
            log.info("Created demo user {}", email);
        }
    }
}
