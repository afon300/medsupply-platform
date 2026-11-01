package com.medsupply.hospital.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsAndWebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsAndWebConfig(
            @Value("${medsupply.hospital.cors-allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String originsCsv) {
        this.allowedOrigins = originsCsv.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Correlation-Id", "X-Warehouse-Node")
                .allowCredentials(false);
    }
}
