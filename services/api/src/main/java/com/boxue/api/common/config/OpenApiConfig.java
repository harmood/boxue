package com.boxue.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI boxueOpenApi() {
        return new OpenAPI().info(new Info()
            .title("博学 API")
            .version("v1")
            .description("博学多端学习平台业务 API。详细契约见 packages/contracts/API-CONTRACT.md。"));
    }
}