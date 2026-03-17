package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private String title;
    private String description;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private ApprovalType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status")
    private ApprovalStatus status;

    private String riskFlag; // NONE, LARGE_TXN, SUSPICIOUS, COMPLIANCE
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime reviewedAt;
    private String notes;

    public enum ApprovalType { WIRE_TRANSFER, CASH_WITHDRAWAL, CREDIT_LIMIT, ACCOUNT_OPENING, COMPLIANCE }
    public enum ApprovalStatus { PENDING, APPROVED, REJECTED, ESCALATED }
}
