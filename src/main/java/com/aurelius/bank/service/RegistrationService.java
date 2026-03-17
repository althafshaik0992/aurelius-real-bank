package com.aurelius.bank.service;

import com.aurelius.bank.model.ClientApplication;
import com.aurelius.bank.model.User;
import com.aurelius.bank.repository.ClientApplicationRepository;
import com.aurelius.bank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {

    @Autowired private UserRepository userRepo;
    @Autowired private ClientApplicationRepository appRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * Client sets their own password (replaces temp password from staff).
     */
    public void setPassword(String email, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.length() < 8)
            throw new RuntimeException("Password must be at least 8 characters.");
        if (!newPassword.equals(confirmPassword))
            throw new RuntimeException("Passwords do not match.");

        User user = userRepo.findByEmail(email.toLowerCase().trim())
            .orElseThrow(() -> new RuntimeException("No account found for this email."));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    /**
     * Check if email has an approved application with access granted.
     */
    public ClientApplication getGrantedApplication(String email) {
        return appRepo.findByEmail(email.toLowerCase().trim())
            .filter(a -> a.getStatus() == ClientApplication.ApplicationStatus.ACCESS_GRANTED)
            .orElse(null);
    }
}
