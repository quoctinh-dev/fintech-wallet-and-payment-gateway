package com.fintech.wallet.common.outbox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendEvent(String topic, String aggregateId, String payload) {
        // Tham số thứ 2 là aggregateId (key của Kafka) giúp đảm bảo thứ tự message
        kafkaTemplate.send(topic, aggregateId, payload)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Đã gửi message thành công tới topic {}: {}", topic, payload);
                    } else {
                        log.error("Lỗi khi gửi message tới topic {}", topic, ex);
                    }
                });
    }
}
