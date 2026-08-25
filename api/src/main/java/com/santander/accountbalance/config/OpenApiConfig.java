package com.santander.accountbalance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI saldoOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("API de Consulta de Saldo | Santander")
                                .description("""
                                        Consulta de saldo mais recente de uma conta bancária.
                                        """)
                                .version("1.0.0"));
    }

}
