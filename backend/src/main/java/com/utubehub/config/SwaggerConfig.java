package com.utubehub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI utubehubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UTubeHub API Documentation")
                        .description("REST API service endpoints for YouTube Subscription Management, Content Streaming, and AI-Powered Prompt Search.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("UTubeHub Engineering")
                                .email("dev@utubehub.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}
