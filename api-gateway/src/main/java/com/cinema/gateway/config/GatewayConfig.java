package com.cinema.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Movie Service Routes
                .route("movies", r -> r
                        .path("/api/movies/**")
                        .uri("lb://movie-service"))

                // Booking Service Routes - priorità per performance
                .route("bookings", r -> r
                        .path("/api/bookings/**")
                        .uri("lb://booking-service"))

                // Notification Service Routes
                .route("notifications", r -> r
                        .path("/api/notifications/**")
                        .uri("lb://notification-service"))
                .build();
    }
}