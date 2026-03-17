package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    private String description;
    private String category;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type")
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_status")
    private TransactionStatus status;

    private LocalDateTime createdAt = LocalDateTime.now();
    private String referenceId;
    private boolean flagged = false;

    public enum TransactionType { CREDIT, DEBIT }
    public enum TransactionStatus { COMPLETED, PENDING, FAILED, DISPUTED }
}
