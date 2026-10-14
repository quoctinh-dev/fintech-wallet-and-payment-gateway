package com.fintech.wallet.common.outbox.scheduler;

import com.fintech.wallet.common.outbox.entity.OutboxEvent;
import com.fintech.wallet.common.outbox.repository.OutboxEventRepository;
import com.fintech.wallet.common.outbox.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true")
public class OutboxEventScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Phát hiện {} sự kiện Outbox đang chờ xử lý", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaProducerService.sendEvent(event.getTopic(), event.getAggregateId(), event.getPayload());
                event.setStatus("PROCESSED");
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Lỗi khi xử lý Outbox Event ID: {}", event.getId(), e);
            }
        }
    }
}
