package com.roost.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} for background work that must not run on a
 * request thread -- e.g. the Overpass API lookup in
 * NearbyFacilitiesService, which can take up to its own 10s timeout and
 * has no business making a GPS-verification request wait on it.
 *
 * A small dedicated pool, distinct from Spring's default
 * SimpleAsyncTaskExecutor (which spins up an unbounded number of
 * unpooled threads), so a burst of slow third-party calls can't exhaust
 * server threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    public static final String ENRICHMENT_EXECUTOR = "enrichmentExecutor";

    @Bean(ENRICHMENT_EXECUTOR)
    public Executor enrichmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("enrichment-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return enrichmentExecutor();
    }
}
