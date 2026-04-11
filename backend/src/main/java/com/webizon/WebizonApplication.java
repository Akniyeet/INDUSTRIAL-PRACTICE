package com.webizon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Webizon API — multi-tenant webinar SaaS.
 *
 * <p>Virtual threads are enabled via {@code spring.threads.virtual.enabled=true} in
 * {@code application.yml}, making blocking I/O calls non-blocking from a throughput
 * perspective on Java 21.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class WebizonApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebizonApplication.class, args);
    }
}
