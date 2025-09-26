package com.cinema.booking.client;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione Feign Client ottimizzata per performance
 */
@Configuration
public class MovieServiceClientConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC; // Log essenziale per debug
    }

}