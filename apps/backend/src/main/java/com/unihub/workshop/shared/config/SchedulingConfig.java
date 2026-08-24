package com.unihub.workshop.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled task infrastructure for the Core Backend.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
