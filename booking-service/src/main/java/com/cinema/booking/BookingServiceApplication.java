package com.cinema.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableJpaRepositories
@EnableAsync
public class BookingServiceApplication {

    public static void main(String[] args) {
        // Virtual Threads per performance mission-critical - Java 21
        System.setProperty("spring.threads.virtual.enabled", "true");
        System.out.println("Booking Service - Mission Critical Mode");
        System.out.println("Virtual Threads enabled for high concurrency");

        SpringApplication.run(BookingServiceApplication.class, args);
    }
}