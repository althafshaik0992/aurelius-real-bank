package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private String badgeId;     // for staff
    private String clientTier;  // PLATINUM, PRIVATE, STANDARD (for clients)
    private boolean active = true;

    public enum UserRole {
        CLIENT, SUPPORT_AGENT, BRANCH_MANAGER, COMPLIANCE_OFFICER, SUPER_ADMIN
    }
}
