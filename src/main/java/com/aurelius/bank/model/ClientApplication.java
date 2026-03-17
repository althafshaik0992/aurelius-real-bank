package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "client_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Staff who created this application
    @ManyToOne
    @JoinColumn(name = "created_by_staff_id")
    private User createdByStaff;

    // The user record created for this client (set after approval)
    @OneToOne
    @JoinColumn(name = "client_user_id")
    private User clientUser;

    // ── Personal Details ────────────────────────────────────────────────────
    @Column(name="full_name")
    private String fullName;
    private String email;             // This email is what the client uses to login
    @Column(name="date_of_birth")
    private LocalDate dateOfBirth;
    private String nationality;
    @Column(name="phone_number")
    private String phoneNumber;

    // ── Address ─────────────────────────────────────────────────────────────
    @Column(name="address_line1")
    private String addressLine1;
    @Column(name="address_line2")
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private String country;

    // ── Identity Document ────────────────────────────────────────────────────
    @Column(name="id_type")
    private String idType;           // PASSPORT, DRIVERS_LICENSE, NATIONAL_ID
    @Column(name="id_number")
    private String idNumber;
    @Column(name="id_expiry")
    private LocalDate idExpiry;
    @Column(name="id_document_path")
    private String idDocumentPath;   // simulated file path
    @Column(name="id_document_name")
    private String idDocumentName;   // original filename

    // ── Account to open ──────────────────────────────────────────────────────
    @Column(name="account_type")
    private String accountType;      // CHECKING, SAVINGS, BROKERAGE, CREDIT_CARD
    @Column(name="initial_tier")
    private String initialTier;      // STANDARD, PRIVATE, PLATINUM

    // ── Application Status ───────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "app_status")
    private ApplicationStatus status = ApplicationStatus.PENDING_REVIEW;

    @Column(name="rejection_reason")
    private String rejectionReason;
    @Column(name="review_notes")
    private String reviewNotes;

    @ManyToOne
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name="created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name="reviewed_at")
    private LocalDateTime reviewedAt;

    // Has the client been sent their online access invite?
    @Column(name="online_access_granted")
    private boolean onlineAccessGranted = false;
    @Column(name="online_access_granted_at")
    private LocalDateTime onlineAccessGrantedAt;

    public enum ApplicationStatus {
        PENDING_REVIEW,   // Staff submitted, awaiting compliance review
        UNDER_REVIEW,     // Being reviewed
        APPROVED,         // Documents OK — account created, online access can be granted
        REJECTED,         // Application rejected
        ACCESS_GRANTED    // Client has been given login access
    }
}
