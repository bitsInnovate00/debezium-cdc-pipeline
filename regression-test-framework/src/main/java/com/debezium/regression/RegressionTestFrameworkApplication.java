package com.debezium.regression;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the CDC Regression Test Framework.
 *
 * <p>This application provides a REST API and background workers that:
 * <ul>
 *   <li>Manage named test sessions with Kafka offset boundaries</li>
 *   <li>Capture CDC events within session windows</li>
 *   <li>Store golden baselines in PostgreSQL</li>
 *   <li>Compare new runs against approved baselines</li>
 *   <li>Generate JUnit-XML and HTML regression reports</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RegressionTestFrameworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegressionTestFrameworkApplication.class, args);
    }
}
