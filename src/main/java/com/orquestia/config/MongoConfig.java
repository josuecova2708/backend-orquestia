package com.orquestia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Habilita auditoría de MongoDB automática.
 * Esto permite que @CreatedDate y @LastModifiedDate se llenen solos.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
