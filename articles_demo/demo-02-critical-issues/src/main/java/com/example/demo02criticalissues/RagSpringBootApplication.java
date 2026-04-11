package com.example.demo02criticalissues;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ClassName: RagSpringBootApplication
 * Package: com.example.demo02criticalissues
 *
 * @Author Mrchen
 */
@SpringBootApplication
@EnableAsync
public class RagSpringBootApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagSpringBootApplication.class, args);
    }
}
