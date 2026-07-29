package com.dbtraining.reconx.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for audit event publishing.
 *
 * Can be customized via application.yml:
 *   reconx:
 *     audit:
 *       enabled: true
 *       topic: audit-events
 */
@ConfigurationProperties("reconx.audit")
public class AuditProperties {
    private boolean enabled = true;
    private String topic = "audit-events";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
