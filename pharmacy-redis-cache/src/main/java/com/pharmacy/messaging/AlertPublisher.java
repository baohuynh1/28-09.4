package com.pharmacy.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.config.RedisPubSubConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** PRODUCER: đẩy message lên channel pharmacy-alerts. */
@Service
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AlertPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(PharmacyAlert alert) {
        try {
            // Đối tượng -> chuỗi JSON bằng ObjectMapper
            String json = objectMapper.writeValueAsString(alert);

            // PUBLISH pharmacy-alerts <json>  -> trả về số subscriber đang nghe
            Long receivers = redisTemplate.convertAndSend(RedisPubSubConfig.ALERT_CHANNEL, json);

            log.info("[PUBLISHER] Đã gửi lên '{}': {} -> {} subscriber nhận được",
                    RedisPubSubConfig.ALERT_CHANNEL, json, receivers);
            // Fire-and-forget: nếu receivers = 0 thì message mất luôn, Redis không lưu lại.
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không chuyển được alert sang JSON", e);
        }
    }
}
