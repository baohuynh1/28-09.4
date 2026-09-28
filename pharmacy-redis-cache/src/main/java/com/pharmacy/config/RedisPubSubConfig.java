package com.pharmacy.config;

import com.pharmacy.messaging.AlertSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    public static final String ALERT_CHANNEL = "pharmacy-alerts";

    @Bean
    public ChannelTopic alertTopic() {
        return new ChannelTopic(ALERT_CHANNEL);
    }

    /**
     * Container giữ kết nối SUBSCRIBE tới Redis và chuyển message đến listener.
     * Mỗi instance ứng dụng (mỗi Dashboard/chi nhánh) có một container riêng
     * -> tất cả đều nhận được cùng một message (broadcast).
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            AlertSubscriber alertSubscriber,
            ChannelTopic alertTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(alertSubscriber, alertTopic);
        return container;
    }
}
