package com.tcl.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * Main entry point of the backend API.
 *
 * @SpringBootApplication tells Spring Boot to:
 * - scan com.tcl.backend and its subpackages for beans
 * - apply Spring Boot auto-configuration
 * - start the embedded web server
 */
@SpringBootApplication
public class BackendApiApplication {

    public static void main(String[] args) {
        /*
         * Starts the whole Spring application.
         *
         * After this line runs, Spring creates controllers, services,
         * repositories, security filters, RabbitMQ listeners, and database
         * connections based on the code and application.yml configuration.
         */
        SpringApplication.run(BackendApiApplication.class, args);
    }
}
