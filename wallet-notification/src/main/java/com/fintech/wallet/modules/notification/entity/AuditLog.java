package com.fintech.wallet.modules.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String transactionId;
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime receivedAt;
}
