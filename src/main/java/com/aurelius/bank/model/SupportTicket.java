package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticketNumber;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status")
    private TicketStatus status;

    private String category;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;
    private String resolution;

    public enum Priority { CRITICAL, HIGH, MEDIUM, LOW }
    public enum TicketStatus { OPEN, IN_PROGRESS, ESCALATED, RESOLVED, CLOSED }
}
