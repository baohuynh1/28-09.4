package com.pharmacy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * - Value lưu dạng JSON (thay cho byte array mặc định) để dễ xem trong Redis.
 * - Key dạng "medicines::1".
 * - TTL: mặc định lấy từ spring.cache.redis.time-to-live,
 *        riêng cache "medicines" = app.cache.medicines-ttl (60 phút).
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class RedisCacheConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties cacheProperties) {
        CacheProperties.Redis redis = cacheProperties.getRedis();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        if (redis.getTimeToLive() != null) config = config.entryTtl(redis.getTimeToLive());
        if (!redis.isCacheNullValues())    config = config.disableCachingNullValues();
        return config;
    }

    /** Cấu hình TTL riêng cho cache "medicines": 60 phút. */
    @Bean
    public RedisCacheManagerBuilderCustomizer medicinesCacheCustomizer(
            RedisCacheConfiguration baseConfig,
            @Value("${app.cache.medicines-ttl:60m}") Duration medicinesTtl) {
        return builder -> builder.withCacheConfiguration("medicines", baseConfig.entryTtl(medicinesTtl));
    }
}
