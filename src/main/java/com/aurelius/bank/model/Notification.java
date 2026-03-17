package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String title;

    @Column(length = 1000)
    private String message;

    private String type;
    private String icon;

    // 'read' is a MySQL reserved word — use column name 'opened' to avoid SQL errors
    @Column(name = "opened")
    private boolean read = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}
