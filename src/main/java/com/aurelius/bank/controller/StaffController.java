package com.aurelius.bank.controller;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import com.aurelius.bank.service.BankService;
import com.aurelius.bank.service.OnboardingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/staff")
public class StaffController {

    @Autowired private UserRepository userRepo;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private SupportTicketRepository ticketRepo;
    @Autowired private ApprovalRepository approvalRepo;
    @Autowired private ClientApplicationRepository appRepo;
    @Autowired private NotificationRepository notifRepo;
    @Autowired private OnboardingService onboardingService;
    @Autowired private BankService bankService;

    private User me(Authentication auth) {
        return userRepo.findByEmail(auth.getName()).orElseThrow();
    }

    // ── DASHBOARD ─────────────────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User staff = me(auth);

        var pendingApps = appRepo.findByStatusOrderByCreatedAtDesc(
                ClientApplication.ApplicationStatus.PENDING_REVIEW);
        var underReviewApps = appRepo.findByStatusOrderByCreatedAtDesc(
                ClientApplication.ApplicationStatus.UNDER_REVIEW);

        var openTicketsList = ticketRepo.findByStatusOrderByCreatedAtDesc(
                SupportTicket.TicketStatus.OPEN);
        var escalatedTicketsList = ticketRepo.findByStatusOrderByCreatedAtDesc(
                SupportTicket.TicketStatus.ESCALATED);
        var inProgressTicketsList = ticketRepo.findByStatusOrderByCreatedAtDesc(
                SupportTicket.TicketStatus.IN_PROGRESS);
        var resolvedTicketsList = ticketRepo.findByStatusOrderByCreatedAtDesc(
                SupportTicket.TicketStatus.RESOLVED);

        var pendingApprovalList = approvalRepo.findByStatusOrderByCreatedAtDesc(
                Approval.ApprovalStatus.PENDING);
        var approvalLog = approvalRepo.findAllByOrderByCreatedAtDesc();

        int queueCount = openTicketsList.size() + escalatedTicketsList.size() + inProgressTicketsList.size();

        int criticalTicketCount = (int) ticketRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> t.getPriority() != null
                        && "CRITICAL".equalsIgnoreCase(t.getPriority().name())
                        && t.getStatus() != SupportTicket.TicketStatus.RESOLVED)
                .count();

        int alertsSentToday = (int) notifRepo.findAll().stream()
                .filter(n -> n.getCreatedAt() != null
                        && n.getCreatedAt().toLocalDate().equals(LocalDate.now()))
                .count();

        model.addAttribute("staff", staff);
        model.addAttribute("totalClients", userRepo.findByRole(User.UserRole.CLIENT).size());

        model.addAttribute("openTickets", openTicketsList.size());
        model.addAttribute("openTicketCount", openTicketsList.size());
        model.addAttribute("escalatedTicketCount", escalatedTicketsList.size());
        model.addAttribute("inProgressTicketCount", inProgressTicketsList.size());
        model.addAttribute("resolvedTicketCount", resolvedTicketsList.size());
        model.addAttribute("criticalTicketCount", criticalTicketCount);
        model.addAttribute("queueCount", queueCount);

        model.addAttribute("pendingApprovals", pendingApprovalList);
        model.addAttribute("pendingApprovalCount", pendingApprovalList.size());
        model.addAttribute("approvalLog", approvalLog);

        model.addAttribute("pendingApplications", pendingApps.size() + underReviewApps.size());
        model.addAttribute("recentApplications",
                appRepo.findAllByOrderByCreatedAtDesc().stream().limit(5).toList());
        model.addAttribute("recentTickets",
                ticketRepo.findAllByOrderByCreatedAtDesc().stream().limit(5).toList());

        model.addAttribute("alertsSentToday", alertsSentToday);

        return "staff/dashboard";
    }

    // ── CLIENT APPLICATIONS ──────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/applications")
    public String applications(Model model,
                               Authentication auth,
                               @RequestParam(required = false) String status) {
        model.addAttribute("staff", me(auth));

        List<ClientApplication> apps = status != null && !status.isBlank()
                ? appRepo.findByStatusOrderByCreatedAtDesc(ClientApplication.ApplicationStatus.valueOf(status))
                : appRepo.findAllByOrderByCreatedAtDesc();

        model.addAttribute("applications", apps);
        model.addAttribute("statusFilter", status);
        model.addAttribute("pendingCount",
                appRepo.findByStatusOrderByCreatedAtDesc(
                        ClientApplication.ApplicationStatus.PENDING_REVIEW).size());

        return "staff/applications";
    }

    // ── NEW APPLICATION FORM ─────────────────────────────────────────────────
    @GetMapping("/applications/new")
    public String newApplication(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));
        return "staff/application-new";
    }

    @PostMapping("/applications/new")
    public String submitApplication(@RequestParam String fullName,
                                    @RequestParam String email,
                                    @RequestParam String dateOfBirth,
                                    @RequestParam String nationality,
                                    @RequestParam String phoneNumber,
                                    @RequestParam String addressLine1,
                                    @RequestParam(required = false, defaultValue = "") String addressLine2,
                                    @RequestParam String city,
                                    @RequestParam(required = false, defaultValue = "") String state,
                                    @RequestParam String zipCode,
                                    @RequestParam String country,
                                    @RequestParam(defaultValue = "PASSPORT") String idType,
                                    @RequestParam String idNumber,
                                    @RequestParam String idExpiry,
                                    @RequestParam(required = false) String idDocumentName,
                                    @RequestParam(required = false) org.springframework.web.multipart.MultipartFile idDocument,
                                    @RequestParam String accountType,
                                    @RequestParam(defaultValue = "STANDARD") String tier,
                                    Authentication auth,
                                    RedirectAttributes ra) {
        try {
            if (fullName == null || fullName.trim().isEmpty()) {
                throw new RuntimeException("Full name is required.");
            }
            if (email == null || email.trim().isEmpty()) {
                throw new RuntimeException("Email is required.");
            }
            if (dateOfBirth == null || dateOfBirth.trim().isEmpty()) {
                throw new RuntimeException("Date of birth is required.");
            }
            if (idNumber == null || idNumber.trim().isEmpty()) {
                throw new RuntimeException("ID number is required.");
            }
            if (idExpiry == null || idExpiry.trim().isEmpty()) {
                throw new RuntimeException("ID expiry date is required.");
            }
            if (accountType == null || accountType.trim().isEmpty()) {
                throw new RuntimeException("Account type is required.");
            }

            String docName = "kyc_document.jpg";
            if (idDocument != null && !idDocument.isEmpty()) {
                docName = idDocument.getOriginalFilename();
            } else if (idDocumentName != null && !idDocumentName.trim().isEmpty()) {
                docName = idDocumentName.trim();
            }

            onboardingService.createApplication(
                    me(auth),
                    fullName.trim(),
                    email.trim(),
                    dateOfBirth,
                    nationality,
                    phoneNumber,
                    addressLine1.trim(),
                    addressLine2,
                    city.trim(),
                    state,
                    zipCode.trim(),
                    country,
                    idType,
                    idNumber.trim(),
                    idExpiry,
                    docName,
                    accountType,
                    tier
            );

            ra.addFlashAttribute("success",
                    "✅ Application for " + fullName.trim() + " submitted! Pending compliance review.");
            return "redirect:/staff/applications";

        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
            return "redirect:/staff/applications/new";
        }
    }

    // ── APPLICATION REVIEW ───────────────────────────────────────────────────
    @GetMapping("/applications/review")
    public String reviewApplication(@RequestParam("id") Long id,
                                    Model model,
                                    Authentication auth,
                                    RedirectAttributes ra,
                                    jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        Optional<ClientApplication> appOpt = appRepo.findById(id);
        if (appOpt.isEmpty()) {
            ra.addFlashAttribute("error", "Application not found.");
            return "redirect:/staff/applications";
        }

        model.addAttribute("staff", me(auth));
        model.addAttribute("clientApplication", appOpt.get());
        return "staff/application-detail";
    }

    // ── APPROVE APPLICATION ──────────────────────────────────────────────────
    @PostMapping("/applications/{id}/approve")
    public String approveApplication(@PathVariable Long id,
                                     @RequestParam(required = false) String notes,
                                     Authentication auth,
                                     RedirectAttributes ra) {
        try {
            onboardingService.approveApplication(id, me(auth), notes);
            ra.addFlashAttribute("success", "Application approved. You can now grant online access.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/applications/review?id=" + id;
    }

    // ── REJECT APPLICATION ───────────────────────────────────────────────────
    @PostMapping("/applications/{id}/reject")
    public String rejectApplication(@PathVariable Long id,
                                    @RequestParam String reason,
                                    Authentication auth,
                                    RedirectAttributes ra) {
        try {
            onboardingService.rejectApplication(id, me(auth), reason);
            ra.addFlashAttribute("success", "Application rejected. Client will be notified.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/applications/review?id=" + id;
    }

    // ── DELETE APPLICATION ───────────────────────────────────────────────────
    @PostMapping("/applications/{id}/delete")
    public String deleteApplication(@PathVariable Long id,
                                    Authentication auth,
                                    RedirectAttributes ra) {
        try {
            ClientApplication app = appRepo.findById(id).orElseThrow();

            if (app.getStatus() == ClientApplication.ApplicationStatus.ACCESS_GRANTED) {
                ra.addFlashAttribute("error", "Cannot delete an application that has already granted online access.");
                return "redirect:/staff/applications/review?id=" + id;
            }

            String name = app.getFullName() != null ? app.getFullName() : "Incomplete application";
            appRepo.delete(app);
            ra.addFlashAttribute("success", "Application for \"" + name + "\" has been deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/applications";
    }

    // ── GRANT ONLINE ACCESS ──────────────────────────────────────────────────
    @PostMapping("/applications/{id}/grant-access")
    public String grantOnlineAccess(@PathVariable Long id,
                                    Authentication auth,
                                    RedirectAttributes ra) {
        try {
            ClientApplication app = onboardingService.grantOnlineAccess(id, me(auth));
            ra.addFlashAttribute(
                    "success",
                    "✅ Online access granted! Client " + app.getFullName() +
                            " can now login at /login with email: " + app.getEmail() +
                            ". They should use /setup-account to set their password."
            );
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/applications/review?id=" + id;
    }

    // ── CLEANUP INCOMPLETE APPLICATIONS ──────────────────────────────────────
    @PostMapping("/applications/cleanup")
    @org.springframework.transaction.annotation.Transactional
    public String cleanupIncomplete(Authentication auth, RedirectAttributes ra) {
        var incomplete = appRepo.findIncomplete();
        int jpqlCount = incomplete.size();

        if (!incomplete.isEmpty()) {
            appRepo.deleteAll(incomplete);
        }

        int nativeCount = appRepo.deleteAllIncompleteNative();
        int total = jpqlCount + nativeCount;

        if (total == 0) {
            ra.addFlashAttribute("success", "✅ No incomplete applications found — list is clean.");
        } else {
            ra.addFlashAttribute("success", "🧹 Deleted " + total + " incomplete application(s).");
        }

        return "redirect:/staff/applications";
    }

    // ── CUSTOMERS ────────────────────────────────────────────────────────────
    @GetMapping("/customers")
    public String customers(Model model,
                            Authentication auth,
                            @RequestParam(required = false) String search) {
        model.addAttribute("staff", me(auth));

        List<User> clients = userRepo.findByRole(User.UserRole.CLIENT);
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            clients = clients.stream()
                    .filter(c -> c.getFullName().toLowerCase().contains(q)
                            || c.getEmail().toLowerCase().contains(q))
                    .toList();
        }

        model.addAttribute("clients", clients);
        model.addAttribute("search", search);
        return "staff/customers";
    }

    // ── CUSTOMER 360 ─────────────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/customer360/{id}")
    public String customer360(@PathVariable Long id, Model model, Authentication auth) {
        User customer = userRepo.findById(id).orElseThrow();
        List<Account> accounts = accountRepo.findByOwner(customer);
        List<Transaction> transactions = txRepo.findByAccount_OwnerOrderByCreatedAtDesc(customer)
                .stream()
                .limit(10)
                .toList();
        List<SupportTicket> tickets = ticketRepo.findByCustomerOrderByCreatedAtDesc(customer);
        ClientApplication application = appRepo.findByClientUser(customer).orElse(null);

        BigDecimal totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String kycStatus = "NOT AVAILABLE";
        if (application != null && application.getStatus() != null) {
            if (application.getStatus() == ClientApplication.ApplicationStatus.ACCESS_GRANTED
                    || application.getStatus() == ClientApplication.ApplicationStatus.APPROVED) {
                kycStatus = "VERIFIED";
            } else if (application.getStatus() == ClientApplication.ApplicationStatus.REJECTED) {
                kycStatus = "REJECTED";
            } else {
                kycStatus = application.getStatus().name().replace('_', ' ');
            }
        }

        LocalDateTime lastActivity = null;
        if (!transactions.isEmpty()) {
            lastActivity = transactions.get(0).getCreatedAt();
        } else if (!tickets.isEmpty()) {
            lastActivity = tickets.get(0).getCreatedAt();
        } else if (application != null) {
            lastActivity = application.getCreatedAt();
        }

        LocalDateTime memberSince = application != null ? application.getCreatedAt() : null;

        int riskScore = 18;
        String riskLabel = "LOW";

        long openOrEscalatedTickets = tickets.stream()
                .filter(t -> t.getStatus() == SupportTicket.TicketStatus.OPEN
                        || t.getStatus() == SupportTicket.TicketStatus.ESCALATED)
                .count();

        if (openOrEscalatedTickets >= 3) {
            riskScore = 72;
            riskLabel = "HIGH";
        } else if (openOrEscalatedTickets >= 1) {
            riskScore = 44;
            riskLabel = "MEDIUM";
        }

        model.addAttribute("staff", me(auth));
        model.addAttribute("customer", customer);
        model.addAttribute("accounts", accounts);
        model.addAttribute("totalBalance", totalBalance);
        model.addAttribute("transactions", transactions);
        model.addAttribute("tickets", tickets);
        model.addAttribute("application", application);

        model.addAttribute("kycStatus", kycStatus);
        model.addAttribute("lastActivity", lastActivity);
        model.addAttribute("memberSince", memberSince);
        model.addAttribute("riskScore", riskScore);
        model.addAttribute("riskLabel", riskLabel);

        return "staff/customer360";
    }

    // ── FREEZE / UNFREEZE ACCOUNT ────────────────────────────────────────────
    @PostMapping("/customer360/{id}/freeze/{accNum}")
    public String freeze(@PathVariable Long id,
                         @PathVariable String accNum,
                         Authentication auth,
                         RedirectAttributes ra) {
        Account acc = accountRepo.findByAccountNumber(accNum).orElseThrow();
        String newStatus = "FROZEN".equals(acc.getStatus()) ? "ACTIVE" : "FROZEN";

        acc.setStatus(newStatus);
        accountRepo.save(acc);

        Notification n = new Notification();
        n.setUser(acc.getOwner());
        n.setTitle("Account Status Updated");
        n.setMessage("Your account •••• " + accNum + " is now " + newStatus + ".");
        n.setType("ACCOUNT");
        n.setIcon("FROZEN".equals(newStatus) ? "🔒" : "🔓");
        n.setCreatedAt(LocalDateTime.now());
        notifRepo.save(n);

        ra.addFlashAttribute("success", "Account •••• " + accNum + " is now " + newStatus);
        return "redirect:/staff/customer360/" + id;
    }

    // ── ADD TRANSACTION ──────────────────────────────────────────────────────
    @PostMapping("/customer360/{id}/add-transaction")
    public String addTransaction(@PathVariable Long id,
                                 @RequestParam String accountNumber,
                                 @RequestParam String description,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam String type,
                                 Authentication auth,
                                 RedirectAttributes ra) {
        Account account = accountRepo.findByAccountNumber(accountNumber).orElseThrow();

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setDescription(description);
        tx.setCategory("Adjustment");
        tx.setType(Transaction.TransactionType.valueOf(type));
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setReferenceId("ADJ-" + System.currentTimeMillis());

        if ("CREDIT".equals(type)) {
            tx.setAmount(amount);
            account.setBalance(account.getBalance().add(amount));
        } else {
            tx.setAmount(amount.negate());
            account.setBalance(account.getBalance().subtract(amount));
        }

        txRepo.save(tx);
        accountRepo.save(account);

        ra.addFlashAttribute("success", "Transaction recorded.");
        return "redirect:/staff/customer360/" + id;
    }

    // ── APPROVALS ────────────────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/approvals")
    public String approvals(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));
        model.addAttribute("pendingApprovals",
                approvalRepo.findByStatusOrderByCreatedAtDesc(Approval.ApprovalStatus.PENDING));
        model.addAttribute("approvalLog",
                approvalRepo.findAllByOrderByCreatedAtDesc());
        return "staff/approvals";
    }

    @PostMapping("/approvals/{id}/action")
    public String approvalAction(@PathVariable Long id,
                                 @RequestParam String action,
                                 Authentication auth,
                                 RedirectAttributes ra) {
        Approval apr = approvalRepo.findById(id).orElseThrow();
        apr.setStatus("APPROVE".equals(action)
                ? Approval.ApprovalStatus.APPROVED
                : Approval.ApprovalStatus.REJECTED);
        apr.setReviewedBy(me(auth));
        apr.setReviewedAt(LocalDateTime.now());
        approvalRepo.save(apr);

        ra.addFlashAttribute("success", "Approval " + action.toLowerCase() + "d.");
        return "redirect:/staff/approvals";
    }

    // ── TICKETS ──────────────────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/tickets")
    public String tickets(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));
        model.addAttribute("tickets", ticketRepo.findAllByOrderByCreatedAtDesc());
        return "staff/tickets";
    }

    @PostMapping("/tickets/{id}/resolve")
    public String resolveTicket(@PathVariable Long id,
                                Authentication auth,
                                RedirectAttributes ra) {
        SupportTicket t = ticketRepo.findById(id).orElseThrow();
        t.setStatus(SupportTicket.TicketStatus.RESOLVED);
        t.setResolvedAt(LocalDateTime.now());
        ticketRepo.save(t);

        ra.addFlashAttribute("success", "Ticket resolved.");
        return "redirect:/staff/tickets";
    }

    // ── ALERTS ───────────────────────────────────────────────────────────────
    @GetMapping("/alerts")
    public String alerts(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));
        model.addAttribute("clients", userRepo.findByRole(User.UserRole.CLIENT));
        return "staff/alerts";
    }

    @PostMapping("/alerts/send")
    public String sendAlert(@RequestParam Long recipientId,
                            @RequestParam String subject,
                            @RequestParam String message,
                            Authentication auth,
                            RedirectAttributes ra) {
        User client = userRepo.findById(recipientId).orElseThrow();

        Notification n = new Notification();
        n.setUser(client);
        n.setTitle(subject);
        n.setMessage(message);
        n.setType("GENERAL");
        n.setIcon("📢");
        n.setCreatedAt(LocalDateTime.now());
        notifRepo.save(n);

        ra.addFlashAttribute("success", "Alert sent to " + client.getFullName() + ".");
        return "redirect:/staff/alerts";
    }

    // ── REPORTS ──────────────────────────────────────────────────────────────
    @GetMapping("/reports")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String reports(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));
        model.addAttribute("totalClients", userRepo.findByRole(User.UserRole.CLIENT).size());
        model.addAttribute("totalAccounts", accountRepo.count());
        model.addAttribute("totalTransactions", txRepo.count());
        model.addAttribute("openTickets",
                ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN).size());
        model.addAttribute("resolvedTickets",
                ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size());
        model.addAttribute("escalatedTickets",
                ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.ESCALATED).size());
        model.addAttribute("pendingApprovals",
                approvalRepo.findByStatusOrderByCreatedAtDesc(Approval.ApprovalStatus.PENDING).size());
        model.addAttribute("totalApplications", appRepo.count());
        model.addAttribute("accessGranted",
                appRepo.findByStatusOrderByCreatedAtDesc(
                        ClientApplication.ApplicationStatus.ACCESS_GRANTED).size());
        model.addAttribute("pendingApplications",
                appRepo.findByStatusOrderByCreatedAtDesc(
                        ClientApplication.ApplicationStatus.PENDING_REVIEW).size());
        model.addAttribute("recentTransactions",
                txRepo.findAll(
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                10,
                                org.springframework.data.domain.Sort.by("createdAt").descending()
                        )
                ).getContent());

        return "staff/reports";
    }

    // ── AUDIT LOG ────────────────────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @GetMapping("/audit-log")
    public String auditLog(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));
        model.addAttribute("tickets", ticketRepo.findAllByOrderByCreatedAtDesc());
        return "staff/audit-log";
    }

    // ── CALL QUEUE ───────────────────────────────────────────────────────────
    @GetMapping("/call-queue")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String callQueue(Model model, Authentication auth) {
        model.addAttribute("staff", me(auth));

        var activeTickets = ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.IN_PROGRESS);
        var openTickets = ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.OPEN);
        var escalated = ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.ESCALATED);

        var queuedTickets = new java.util.ArrayList<SupportTicket>();
        queuedTickets.addAll(escalated);
        queuedTickets.addAll(openTickets);

        model.addAttribute("activeTickets", activeTickets);
        model.addAttribute("queuedTickets", queuedTickets);
        model.addAttribute("queueSize", queuedTickets.size() + activeTickets.size());
        model.addAttribute("resolvedToday",
                ticketRepo.findByStatusOrderByCreatedAtDesc(SupportTicket.TicketStatus.RESOLVED).size());

        return "staff/call-queue";
    }
}