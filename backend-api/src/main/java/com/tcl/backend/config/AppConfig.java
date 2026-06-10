package com.tcl.backend.config;

import com.tcl.backend.messaging.RabbitQueueProperties;
import com.tcl.backend.messaging.OnChainProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/*
 * General application configuration.
 *
 * This class has no methods because its job is only to hold Spring configuration
 * annotations. Empty configuration classes are normal in Spring Boot when an
 * annotation itself activates a feature.
 */
@Configuration
/*
 * Enable binding application.yml values into RabbitQueueProperties.
 *
 * RabbitQueueProperties reads:
 * app.rabbitmq.mempool-queue
 * app.rabbitmq.event-bus-queue
 * app.onchain.wallet-base-url
 * app.onchain.internal-api-key
 * app.onchain.timeout-ms
 *
 * Without this annotation, Spring may not create these configuration-property
 * objects as beans, and other classes could not inject them in constructors.
 */
@EnableConfigurationProperties({RabbitQueueProperties.class, OnChainProperties.class})
public class AppConfig {
}
