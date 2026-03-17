package com.aurelius.bank.controller;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import com.aurelius.bank.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/client")
public class ClientController {

    @Autowired private BankService bankService;
    @Autowired private UserRepository userRepo;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private NotificationRepository notifRepo;
    @Autowired private ClientApplicationRepository appRepo;

    private User me(Authentication auth) {
        return userRepo.findByEmail(auth.getName()).orElseThrow();
    }

    private void addCommonAttributes(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("unreadCount", notifRepo.countByUserAndReadFalse(user));
    }

    // ── DASHBOARD ─────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        User user = me(auth);
        List<Account> accounts = accountRepo.findByOwner(user);
        List<Transaction> recentTx = txRepo.findByAccount_OwnerOrderByCreatedAtDesc(user);
        BigDecimal totalBalance = accounts.stream().map(Account::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        addCommonAttributes(model, user);
        model.addAttribute("accounts", accounts);
        model.addAttribute("recentTransactions", recentTx.stream().limit(6).toList());
        model.addAttribute("totalBalance", totalBalance);
        model.addAttribute("justSetup", model.containsAttribute("justSetup"));
        return "client/dashboard";
    }

    // ── ACCOUNTS ─────────────────────────────────────────────────────────────
    @GetMapping("/accounts")
    public String accounts(Model model, Authentication auth) {
        User user = me(auth);
        addCommonAttributes(model, user);
        model.addAttribute("accounts", accountRepo.findByOwner(user));
        return "client/accounts";
    }

    // ── TRANSACTIONS ──────────────────────────────────────────────────────────
    @GetMapping("/transactions")
    public String transactions(Model model, Authentication auth,
                               @RequestParam(required = false) String filter,
                               @RequestParam(required = false) String search,
                               @RequestParam(required = false) String account) {
        User user = me(auth);
        List<Transaction> txList;
        if (account != null && !account.isBlank()) {
            txList = accountRepo.findByAccountNumber(account)
                .map(txRepo::findByAccountOrderByCreatedAtDesc).orElse(List.of());
        } else {
            txList = txRepo.findByAccount_OwnerOrderByCreatedAtDesc(user);
        }
        if (filter != null && !filter.isBlank()) {
            txList = txList.stream()
                .filter(t -> t.getType().name().equalsIgnoreCase(filter) ||
                             t.getStatus().name().equalsIgnoreCase(filter)).toList();
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            txList = txList.stream()
                .filter(t -> t.getDescription().toLowerCase().contains(q)).toList();
        }
        addCommonAttributes(model, user);
        model.addAttribute("transactions", txList);
        model.addAttribute("accounts", accountRepo.findByOwner(user));
        model.addAttribute("filter", filter);
        model.addAttribute("search", search);
        model.addAttribute("selectedAccount", account);
        return "client/transactions";
    }

    // ── TRANSFER ──────────────────────────────────────────────────────────────
    @GetMapping("/transfer")
    public String transfer(Model model, Authentication auth) {
        User user = me(auth);
        addCommonAttributes(model, user);
        model.addAttribute("accounts", accountRepo.findByOwner(user));
        return "client/transfer";
    }

    @PostMapping("/transfer")
    public String doTransfer(@RequestParam String fromAccount,
                             @RequestParam String toAccount,
                             @RequestParam BigDecimal amount,
                             @RequestParam(required = false) String note,
                             Authentication auth, RedirectAttributes ra) {
        try {
            bankService.transfer(fromAccount, toAccount, amount, note);
            ra.addFlashAttribute("success", String.format("$%,.2f transferred successfully!", amount));
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/client/transfer";
    }

    // ── CARDS ─────────────────────────────────────────────────────────────────
    @GetMapping("/cards")
    public String cards(Model model, Authentication auth) {
        User user = me(auth);
        addCommonAttributes(model, user);
        model.addAttribute("cards", accountRepo.findByOwner(user).stream()
            .filter(a -> a.getType() == Account.AccountType.CREDIT_CARD).toList());
        return "client/cards";
    }

    // ── NOTIFICATIONS ─────────────────────────────────────────────────────────
    @GetMapping("/notifications")
    public String notifications(Model model, Authentication auth) {
        User user = me(auth);
        List<Notification> notifs = notifRepo.findByUserOrderByCreatedAtDesc(user);
        notifs.forEach(n -> n.setRead(true));
        notifRepo.saveAll(notifs);
        addCommonAttributes(model, user);
        model.addAttribute("notifications", notifs);
        model.addAttribute("unreadCount", 0L);
        return "client/notifications";
    }

    // ── INVESTMENTS ───────────────────────────────────────────────────────────
    @GetMapping("/investments")
    public String investments(Model model, Authentication auth) {
        User user = me(auth);
        addCommonAttributes(model, user);
        model.addAttribute("brokerageAccounts", accountRepo.findByOwner(user).stream()
            .filter(a -> a.getType() == Account.AccountType.BROKERAGE).toList());
        return "client/investments";
    }

    // ── SETTINGS ──────────────────────────────────────────────────────────────
    @GetMapping("/settings")
    public String settings(Model model, Authentication auth) {
        User user = me(auth);
        addCommonAttributes(model, user);
        model.addAttribute("application", appRepo.findByClientUser(user).orElse(null));
        return "client/settings";
    }
}
