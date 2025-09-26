package com.cinema.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Virtual Threads configuration for mission-critical performance
 * Java 21: Gestisce migliaia di prenotazioni simultanee senza overhead
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig implements AsyncConfigurer {

    @Bean("virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        // Java 21: Virtual Thread per ogni prenotazione simultanea
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public Executor getAsyncExecutor() {
        return virtualThreadExecutor();
    }
}
