package com.aurelius.bank.controller;

import com.aurelius.bank.model.ClientApplication;
import com.aurelius.bank.model.User;
import com.aurelius.bank.repository.UserRepository;
import com.aurelius.bank.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PublicController {

    @Autowired private RegistrationService registrationService;
    @Autowired private UserRepository userRepo;
    @Autowired private AuthenticationManager authenticationManager;

    @GetMapping({"/", "/home"})
    public String home() { return "public/home"; }

    @GetMapping("/login")
    public String login(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return roleRedirect(auth);
        return "public/login";
    }

    @GetMapping("/staff/login")
    public String staffLogin(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return roleRedirect(auth);
        return "public/staff-login";
    }

    @GetMapping("/error")
    public String error() { return "public/error"; }

    // ── First-time setup page — client sets their own password ─────────────
    @GetMapping("/setup-account")
    public String setupAccount(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email);
        return "public/setup-account";
    }

    @PostMapping("/setup-account")
    public String doSetup(@RequestParam String email,
                          @RequestParam String password,
                          @RequestParam String confirmPassword,
                          HttpServletRequest request,
                          RedirectAttributes ra) {
        try {
            // 1. Check the account exists and access was granted by staff
            ClientApplication app = registrationService.getGrantedApplication(email);
            if (app == null) {
                ra.addFlashAttribute("error",
                    "No activated account found for " + email +
                    ". Please contact your bank branch to set up your account.");
                ra.addFlashAttribute("prefillEmail", email);
                return "redirect:/setup-account";
            }

            // 2. Set the new password
            registrationService.setPassword(email, password, confirmPassword);

            // 3. Auto-login
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
            SecurityContextHolder.getContext().setAuthentication(auth);
            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());

            ra.addFlashAttribute("justSetup", true);
            return "redirect:/client/dashboard";

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("prefillEmail", email);
            return "redirect:/setup-account";
        }
    }

    private String roleRedirect(Authentication auth) {
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String r = ga.getAuthority();
            if (r.equals("ROLE_SUPPORT_AGENT") || r.equals("ROLE_BRANCH_MANAGER") ||
                r.equals("ROLE_COMPLIANCE_OFFICER") || r.equals("ROLE_SUPER_ADMIN"))
                return "redirect:/staff/dashboard";
        }
        return "redirect:/client/dashboard";
    }
}
