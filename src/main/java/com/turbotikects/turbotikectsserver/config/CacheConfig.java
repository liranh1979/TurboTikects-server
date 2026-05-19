package com.turbotikects.turbotikectsserver.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // No need to define "sessions" here because it is in infinispan.xml
}
