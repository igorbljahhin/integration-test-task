package com.example.ecommerceorderprocessor;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.integration.config.EnableIntegration;

import java.util.Map;

@SpringBootApplication
@EnableIntegration
public class EcommerceOrderProcessorApplication {

    public static void main(String[] args) {
        final SpringApplication app = new SpringApplicationBuilder(EcommerceOrderProcessorApplication.class)
                .headless(true)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .build();
        app.setDefaultProperties(Map.of("spring.profiles.default", "dev"));

        app.run(args);
    }
}