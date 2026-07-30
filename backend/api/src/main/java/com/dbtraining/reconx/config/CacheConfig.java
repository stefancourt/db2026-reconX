package com.dbtraining.reconx.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache instruments = new CaffeineCache("instruments",
            Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(5, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

        CaffeineCache counterparties = new CaffeineCache("counterparties",
            Caffeine.newBuilder()
                    .maximumSize(200)
                    .expireAfterWrite(1, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

        SimpleCacheManager mgr = new SimpleCacheManager();
        mgr.setCaches(List.of(instruments, counterparties));
        return mgr;
    }
}
