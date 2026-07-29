package com.dbtraining.reconx.observability;

import org.springframework.cache.CacheManager;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * TICKET-ADV096 — JMX MBean exposing runtime tunables for the reconciliation engine.
 *
 * Accessible from JConsole: MBeans → reconx → ReconConfig
 *   - PriceTolerance: read/write double (0.0..1.0) — the engine reads this on each run
 *   - CachingEnabled: read/write boolean toggle
 *   - clearCache():   evict all Caffeine cache entries without restart
 */
@Component
@ManagedResource(
    objectName = "reconx:type=ReconConfig",
    description = "Runtime tuning for the reconciliation engine"
)
public class ReconConfigMBean {

    private volatile double priceTolerance = 0.01;
    private volatile boolean cachingEnabled = true;
    private final CacheManager cacheManager;

    public ReconConfigMBean(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @ManagedAttribute(description = "Price tolerance for break detection (0.0 - 1.0)")
    public double getPriceTolerance() {
        return priceTolerance;
    }

    @ManagedAttribute(description = "Price tolerance for break detection (0.0 - 1.0)")
    public void setPriceTolerance(double v) {
        if (v < 0 || v > 1) {
            throw new IllegalArgumentException("priceTolerance must be between 0.0 and 1.0, got: " + v);
        }
        this.priceTolerance = v;
    }

    @ManagedAttribute(description = "Whether caching is active for instruments and counterparties")
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    @ManagedAttribute(description = "Whether caching is active for instruments and counterparties")
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
    }

    @ManagedOperation(description = "Evict all entries from the instruments and counterparties caches")
    public void clearCache() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
