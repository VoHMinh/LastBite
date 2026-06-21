package com.LastBite.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Cấu hình Redis cache với TTL riêng cho từng cache.
 *
 * Spring Cache lưu Java object nội bộ như List DTO và PageImpl. Dùng JDK serializer
 * để cache hit trả lại đúng object graph, tránh lỗi JSON polymorphic deserialize với
 * GenericJacksonJsonRedisSerializer trên collection/page framework types.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final String CACHE_PREFIX_VERSION = "lastbite:cache:v2:";

    @Bean
    public RedisSerializer<Object> redisCacheValueSerializer() {
        return new JdkSerializationRedisSerializer(getClass().getClassLoader());
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          RedisSerializer<Object> redisCacheValueSerializer) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> CACHE_PREFIX_VERSION + cacheName + "::")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisCacheValueSerializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(15));

        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                "user-profile", defaults.entryTtl(Duration.ofMinutes(30)),
                "user-addresses", defaults.entryTtl(Duration.ofMinutes(30)),
                "store-detail", defaults.entryTtl(Duration.ofMinutes(15)),
                "store-by-slug", defaults.entryTtl(Duration.ofMinutes(15)),
                "store-list", defaults.entryTtl(Duration.ofMinutes(5)),
                "bag-discovery", defaults.entryTtl(Duration.ofSeconds(60)),
                "bag-detail", defaults.entryTtl(Duration.ofSeconds(60)),
                "store-bags", defaults.entryTtl(Duration.ofSeconds(60)),
                "home-discovery", defaults.entryTtl(Duration.ofSeconds(60)),
                "discovery-config", defaults.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCacheConfig)
                .transactionAware()
                .build();
    }
}
