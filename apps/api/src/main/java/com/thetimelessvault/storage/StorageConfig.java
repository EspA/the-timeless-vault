package com.thetimelessvault.storage;

import com.thetimelessvault.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    ObjectStorage objectStorage(AppProperties properties) {
        if (properties.getStorage().gcsEnabled()) {
            return new GcsObjectStorage(properties);
        }
        return new LocalObjectStorage(properties);
    }
}
