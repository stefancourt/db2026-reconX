package com.dbtraining.reconx.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================================
 * OpenApiConfig — TICKET-ADV058
 * ============================================================================
 * WHAT:    Customises the OpenAPI document Springdoc generates (title, version,
 *          description, contact + bearerAuth security scheme).
 * HOW:     Single @Bean of type io.swagger.v3.oas.models.OpenAPI.
 * WHY:     Swagger UI on /api/swagger-ui.html becomes the single source of
 *          truth for the API contract — front-end and QA teams read it
 *          instead of digging through controllers.
 * OBSERVE: After wiring, the title in the top-left of Swagger UI is
 *          "ReconX API" and a green "Authorize" button accepts bearer JWTs.
 * ============================================================================
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI reconxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ReconX API")
                        .version("v1")
                        .description("Enterprise Trade Reconciliation Platform (Advanced Track)")
                        .contact(new Contact().name("DB TDI Training").email("tdi@db.com")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
