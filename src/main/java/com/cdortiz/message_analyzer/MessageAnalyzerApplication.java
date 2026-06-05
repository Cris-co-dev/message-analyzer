package com.cdortiz.message_analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot entry point for the message-analyzer service.
 *
 * <p>Bootstraps the application context and enables class-path scanning of
 * {@link org.springframework.boot.context.properties.ConfigurationProperties}-annotated
 * types (such as {@link com.cdortiz.message_analyzer.config.AlertProperties})
 * via {@link ConfigurationPropertiesScan}, so configuration beans are
 * discovered without an explicit {@code @EnableConfigurationProperties}
 * declaration.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MessageAnalyzerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MessageAnalyzerApplication.class, args);
	}

}
