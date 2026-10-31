package com.company.screening.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Interview Screening & Booking Service API")
                        .version("1.0.0")
                        .description("Microservice for managing interview slot bookings, concurrency sweep-line capacity controls, and automated voice screening lifecycle.")
                        .contact(new Contact()
                                .name("Engineering Team")
                                .email("engineering@company.com")));
    }
}
