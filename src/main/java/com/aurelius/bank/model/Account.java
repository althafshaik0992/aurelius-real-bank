package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    private AccountType type;

    @Column(precision = 15, scale = 2)
    private BigDecimal balance;

    private String status = "ACTIVE"; // ACTIVE, FROZEN, CLOSED
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum AccountType {
        CHECKING, SAVINGS, BROKERAGE, CREDIT_CARD
    }
}
