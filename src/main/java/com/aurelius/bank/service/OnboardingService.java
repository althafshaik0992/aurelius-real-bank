package com.aurelius.bank.service;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OnboardingService {

    @Autowired private ClientApplicationRepository appRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private NotificationRepository notifRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    // ── 1. Staff creates client application + KYC documents ─────────────────
    @Transactional
    public ClientApplication createApplication(User staff,
                                                String fullName,
                                                String email,
                                                String dob,
                                                String nationality,
                                                String phone,
                                                String address1,
                                                String address2,
                                                String city,
                                                String state,
                                                String zip,
                                                String country,
                                                String idType,
                                                String idNumber,
                                                String idExpiry,
                                                String idDocumentName,
                                                String accountType,
                                                String tier) {

        if (appRepo.findByEmail(email.toLowerCase().trim()).isPresent())
            throw new RuntimeException("An application for this email already exists.");
        if (userRepo.findByEmail(email.toLowerCase().trim()).isPresent())
            throw new RuntimeException("A client account with this email already exists.");

        ClientApplication app = new ClientApplication();
        app.setCreatedByStaff(staff);
        app.setFullName(fullName.trim());
        app.setEmail(email.toLowerCase().trim());
        app.setDateOfBirth(LocalDate.parse(dob));
        app.setNationality(nationality);
        app.setPhoneNumber(phone);
        app.setAddressLine1(address1);
        app.setAddressLine2(address2);
        app.setCity(city);
        app.setState(state);
        app.setZipCode(zip);
        app.setCountry(country);
        app.setIdType(idType);
        app.setIdNumber(idNumber);
        app.setIdExpiry(LocalDate.parse(idExpiry));
        app.setIdDocumentPath("uploads/kyc/" + UUID.randomUUID() + ".jpg");
        app.setIdDocumentName(idDocumentName != null ? idDocumentName : "document.jpg");
        app.setAccountType(accountType);
        app.setInitialTier(tier);
        app.setStatus(ClientApplication.ApplicationStatus.PENDING_REVIEW);
        app.setCreatedAt(LocalDateTime.now());
        return appRepo.save(app);
    }

    // ── 2. Compliance approves the application ────────────────────────────────
    @Transactional
    public ClientApplication approveApplication(Long appId, User reviewer, String notes) {
        ClientApplication app = appRepo.findById(appId).orElseThrow();

        app.setStatus(ClientApplication.ApplicationStatus.APPROVED);
        app.setReviewedBy(reviewer);
        app.setReviewNotes(notes);
        app.setReviewedAt(LocalDateTime.now());
        return appRepo.save(app);
    }

    // ── 3. Compliance rejects the application ────────────────────────────────
    @Transactional
    public ClientApplication rejectApplication(Long appId, User reviewer, String reason) {
        ClientApplication app = appRepo.findById(appId).orElseThrow();
        app.setStatus(ClientApplication.ApplicationStatus.REJECTED);
        app.setReviewedBy(reviewer);
        app.setRejectionReason(reason);
        app.setReviewedAt(LocalDateTime.now());
        return appRepo.save(app);
    }

    // ── 4. Grant online access — creates User + Account, sends "invite" ───────
    @Transactional
    public ClientApplication grantOnlineAccess(Long appId, User staff) {
        ClientApplication app = appRepo.findById(appId).orElseThrow();

        if (app.getStatus() != ClientApplication.ApplicationStatus.APPROVED)
            throw new RuntimeException("Application must be APPROVED before granting online access.");
        if (app.isOnlineAccessGranted())
            throw new RuntimeException("Online access has already been granted for this client.");

        // Create the User account
        User client = new User();
        client.setFullName(app.getFullName());
        client.setEmail(app.getEmail());
        // Temporary password: first name + last 4 of ID (client must change on first login)
        String tempPassword = app.getFullName().split(" ")[0].toLowerCase() +
                              app.getIdNumber().substring(Math.max(0, app.getIdNumber().length() - 4));
        client.setPassword(passwordEncoder.encode(tempPassword));
        client.setRole(User.UserRole.CLIENT);
        client.setClientTier(app.getInitialTier() != null ? app.getInitialTier() : "STANDARD");
        client.setActive(true);
        userRepo.save(client);

        // Create the account
        String accNum = generateAccountNumber();
        Account account = new Account();
        account.setAccountNumber(accNum);
        account.setOwner(client);
        account.setType(Account.AccountType.valueOf(app.getAccountType().toUpperCase()));
        account.setBalance(BigDecimal.ZERO);
        account.setStatus("ACTIVE");
        account.setCreatedAt(LocalDateTime.now());
        accountRepo.save(account);

        // Send welcome notification (visible on first login)
        Notification notif = new Notification();
        notif.setUser(client);
        notif.setTitle("Welcome to Aurelius Private Banking! 🏦");
        notif.setMessage("Your account has been verified and activated. " +
            "Your " + app.getAccountType().replace("_", " ") +
            " account ending " + accNum + " is ready to use. " +
            "Temporary password: " + tempPassword + " — please update in Settings.");
        notif.setType("ACCOUNT");
        notif.setIcon("🏦");
        notif.setRead(false);
        notif.setCreatedAt(LocalDateTime.now());
        notifRepo.save(notif);

        // Update application
        app.setClientUser(client);
        app.setOnlineAccessGranted(true);
        app.setOnlineAccessGrantedAt(LocalDateTime.now());
        app.setStatus(ClientApplication.ApplicationStatus.ACCESS_GRANTED);
        return appRepo.save(app);
    }

    // ── When client first logs in, link them to their application ────────────
    public ClientApplication findByClientEmail(String email) {
        return appRepo.findByEmail(email).orElse(null);
    }

    private String generateAccountNumber() {
        String num;
        do {
            num = String.valueOf((int)(Math.random() * 9000 + 1000));
        } while (accountRepo.findByAccountNumber(num).isPresent());
        return num;
    }
}
