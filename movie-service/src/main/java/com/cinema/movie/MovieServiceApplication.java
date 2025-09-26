package com.cinema.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableDiscoveryClient
@EnableJpaRepositories
@EnableCaching
public class MovieServiceApplication {

    public static void main(String[] args) {
        // Virtual Threads per performance - Java 21
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(MovieServiceApplication.class, args);
    }
}