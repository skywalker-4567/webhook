package com.example.razorpaywebhook.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
public class AsyncConfig {

    @Bean("mlExecutor")
    public TaskExecutor mlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ml-async-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        log.info("ML async executor initialized");
        return executor;
    }
}