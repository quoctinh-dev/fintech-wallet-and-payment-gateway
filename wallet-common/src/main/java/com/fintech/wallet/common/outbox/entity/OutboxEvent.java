package com.fintech.wallet.common.outbox.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private String status; // Trạng thái: PENDING, PROCESSED, FAILED

    @CreationTimestamp
    private LocalDateTime createdAt;
}
