package com.mockstudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockStudioApplication.class, args);
    }
}
