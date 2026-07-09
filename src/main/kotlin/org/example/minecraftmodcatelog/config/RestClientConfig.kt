package org.example.minecraftmodcatelog.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {

    @Bean
    fun restClient(): RestClient {
        return RestClient.builder()
            .defaultHeader("User-Agent", "MinecraftModCatalog/0.1 (GoogleDeepMind Antigravity)")
            .build()
    }
}
