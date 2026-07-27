package com.dbtraining.reconx.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================================
 * TICKET-ADV060 — KafkaHealthIndicator (conditional on bootstrap-servers)
 *
 * WHAT:    Actuator HealthIndicator that reports Kafka cluster liveness
 *          (clusterId + nodeCount) using an AdminClient describeCluster call
 *          bounded by 2s request / 3s API timeouts.
 * HOW:     @ConditionalOnProperty gates the bean on
 *          `spring.kafka.bootstrap-servers` — when the property is absent
 *          (e.g. under the `dev` profile) the bean is not registered and the
 *          component does not appear under /actuator/health.
 * WHY:     Day 6 introduces Kafka as the trade-event spine. The Day 8 SRE
 *          runbook wants "is the broker reachable?" without paying the
 *          permanent yellow UNKNOWN cost during local dev.
 * OBSERVE: `curl /api/actuator/health | jq '.components.reconxKafka'` returns
 *          `{"status":"UP","details":{"clusterId":"...","nodeCount":n}}` when
 *          Kafka is up, or is missing entirely under dev.
 * ============================================================================
 */
@Component("reconxKafka")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator extends AbstractHealthIndicator {

    private final String bootstrapServers;

    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        super("ReconX Kafka health check failed");
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        Map<String, Object> cfg = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,     2_000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3_000
        );
        try (AdminClient admin = AdminClient.create(cfg)) {
            DescribeClusterResult cluster = admin.describeCluster();
            String clusterId = cluster.clusterId().get(2, TimeUnit.SECONDS);
            int    nodeCount = cluster.nodes().get(2, TimeUnit.SECONDS).size();
            builder.up()
                   .withDetail("clusterId", clusterId)
                   .withDetail("nodeCount", nodeCount);
        } catch (Exception e) {
            builder.down(e);
        }
    }
}
