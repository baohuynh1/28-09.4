package com.pharmacy.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** CONSUMER: lắng nghe channel pharmacy-alerts và in ra Console. */
@Service
public class AlertSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AlertSubscriber.class);
    private final ObjectMapper objectMapper;

    public AlertSubscriber(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            // Chuỗi JSON -> đối tượng bằng ObjectMapper
            PharmacyAlert alert = objectMapper.readValue(body, PharmacyAlert.class);

            System.out.println("==================================================");
            System.out.println("[DASHBOARD] Nhận thông báo từ channel: " + channel);
            System.out.println("  Loại    : " + alert.type());
            System.out.println("  Nội dung: " + alert.message());
            System.out.println("==================================================");
        } catch (Exception e) {
            log.warn("[DASHBOARD] Message không đúng định dạng JSON: {}", body);
        }
    }
}
