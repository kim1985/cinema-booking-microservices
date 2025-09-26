package com.cinema.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class VirtualThreadConfig {

    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        // Java 21: Virtual Threads per gateway routing
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}