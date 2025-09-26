package com.cinema.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        // Virtual Threads per alta concorrenza nel gateway - Java 21
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(GatewayApplication.class, args);
    }
}