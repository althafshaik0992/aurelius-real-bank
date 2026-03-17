package com.aurelius.bank.controller;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import com.aurelius.bank.service.LoanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.util.*;

@Controller
public class LoanController {

    @Autowired private LoanService loanService;
    @Autowired private LoanRepository loanRepo;
    @Autowired private LoanRepaymentRepository repaymentRepo;
    @Autowired private CreditCheckRepository creditCheckRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private AccountRepository accountRepo;

    private User me(Authentication auth) { return userRepo.findByEmail(auth.getName()).orElseThrow(); }

    // ── CLIENT ─────────────────────────────────────────────────────────────────
    @GetMapping("/client/loans")
    public String loans(Model model, Authentication auth) {
        User u = me(auth);
        model.addAttribute("user", u);
        model.addAttribute("loans", loanRepo.findByClientOrderByAppliedAtDesc(u));
        return "client/loans";
    }

    @GetMapping("/client/loans/select-type")
    public String selectType(Model model, Authentication auth) {
        model.addAttribute("user", me(auth)); return "client/loan-select-type";
    }

    @GetMapping("/client/loans/calculator")
    public String calculator(Model model, Authentication auth) {
        model.addAttribute("user", me(auth)); return "client/loan-calculator";
    }

    @GetMapping("/client/loans/apply/{type}")
    public String applyForm(@PathVariable String type, Model model, Authentication auth) {
        User u = me(auth);
        model.addAttribute("user", u);
        model.addAttribute("accounts", accountRepo.findByOwner(u).stream()
            .filter(a -> "ACTIVE".equals(a.getStatus())).toList());
        model.addAttribute("loanType", type.toUpperCase());
        return "client/loan-apply-" + type.toLowerCase();
    }

    @PostMapping("/client/loans/apply/{type}")
    public String submitLoan(@PathVariable String type,
                              @RequestParam Long accountId,
                              @RequestParam BigDecimal amount,
                              @RequestParam Integer termMonths,
                              @RequestParam String ssn,
                              @RequestParam Map<String, String> allParams,
                              Authentication auth, Model model, RedirectAttributes ra) {
        User u = me(auth);
        try {
            allParams.remove("accountId"); allParams.remove("amount");
            allParams.remove("termMonths"); allParams.remove("ssn"); allParams.remove("_csrf");
            Loan loan = loanService.applyForLoan(u, accountId, type, amount, termMonths, ssn, allParams);
            ra.addFlashAttribute("success", "✅ " + type.substring(0,1).toUpperCase() +
                type.substring(1).toLowerCase() + " loan application " + loan.getLoanNumber() +
                " submitted! Credit check complete — pending staff review.");
            return "redirect:/client/loans";
        } catch (Exception e) {
            model.addAttribute("user", u);
            model.addAttribute("accounts", accountRepo.findByOwner(u).stream()
                .filter(a -> "ACTIVE".equals(a.getStatus())).toList());
            model.addAttribute("loanType", type.toUpperCase());
            model.addAttribute("error", "❌ " + e.getMessage());
            return "client/loan-apply-" + type.toLowerCase();
        }
    }

    @GetMapping("/client/loans/detail")
    public String loanDetail(@RequestParam Long id, Model model, Authentication auth) {
        User u = me(auth);
        Loan loan = loanRepo.findById(id).orElseThrow();
        if (!loan.getClient().getId().equals(u.getId())) return "redirect:/client/loans";
        List<LoanRepayment> schedule = repaymentRepo.findByLoanOrderByInstallmentNumber(loan);
        long paidCount = schedule.stream().filter(r -> r.getRepaymentStatus() == LoanRepayment.RepaymentStatus.PAID).count();
        model.addAttribute("user", u);
        model.addAttribute("loan", loan);
        model.addAttribute("schedule", schedule);
        model.addAttribute("paidCount", paidCount);
        return "client/loan-detail";
    }

    @PostMapping("/client/loans/repay")
    public String repay(@RequestParam Long loanId, Authentication auth, RedirectAttributes ra) {
        try {
            LoanRepayment paid = loanService.makeRepayment(loanId, me(auth));
            ra.addFlashAttribute("success", "✅ Installment #" + paid.getInstallmentNumber() +
                " paid — $" + paid.getAmountDue().setScale(2) + " debited from your account.");
        } catch (Exception e) { ra.addFlashAttribute("error", "❌ " + e.getMessage()); }
        return "redirect:/client/loans/detail?id=" + loanId;
    }

    // ── STAFF ──────────────────────────────────────────────────────────────────
    @GetMapping("/staff/loans")
    public String staffLoans(@RequestParam(required=false) String status, Model model, Authentication auth) {
        List<Loan> loans = (status != null && !status.isEmpty())
            ? loanRepo.findByLoanStatus(Loan.LoanStatus.valueOf(status))
            : loanRepo.findAllByOrderByAppliedAtDesc();
        long pending = loanRepo.countByLoanStatus(Loan.LoanStatus.PENDING) +
                       loanRepo.countByLoanStatus(Loan.LoanStatus.UNDER_REVIEW);
        model.addAttribute("staff", me(auth));
        model.addAttribute("loans", loans);
        model.addAttribute("statusFilter", status);
        model.addAttribute("pendingCount", pending);
        return "staff/loans";
    }

    @GetMapping("/staff/loans/review")
    public String staffReview(@RequestParam Long id, Model model, Authentication auth) {
        Loan loan = loanRepo.findById(id).orElseThrow();
        List<LoanRepayment> schedule = repaymentRepo.findByLoanOrderByInstallmentNumber(loan);
        long paidCount = schedule.stream().filter(r -> r.getRepaymentStatus() == LoanRepayment.RepaymentStatus.PAID).count();
        model.addAttribute("staff", me(auth));
        model.addAttribute("loan", loan);
        model.addAttribute("creditCheck", creditCheckRepo.findByLoan(loan).orElse(null));
        model.addAttribute("schedule", schedule);
        model.addAttribute("paidCount", paidCount);
        return "staff/loan-review";
    }

    @PostMapping("/staff/loans/approve")
    public String approve(@RequestParam Long loanId,
                           @RequestParam(required=false) BigDecimal approvedAmount,
                           @RequestParam(required=false) String notes,
                           Authentication auth, RedirectAttributes ra) {
        try {
            Loan loan = loanService.approveLoan(loanId, me(auth), approvedAmount, notes);
            ra.addFlashAttribute("success", "✅ Loan " + loan.getLoanNumber() + " approved! $" +
                loan.getApprovedAmount().setScale(2) + " disbursed to account ****" + loan.getAccount().getAccountNumber());
        } catch (Exception e) { ra.addFlashAttribute("error", "❌ " + e.getMessage()); }
        return "redirect:/staff/loans/review?id=" + loanId;
    }

    @PostMapping("/staff/loans/reject")
    public String reject(@RequestParam Long loanId, @RequestParam String reason,
                          Authentication auth, RedirectAttributes ra) {
        try {
            loanService.rejectLoan(loanId, me(auth), reason);
            ra.addFlashAttribute("success", "Application rejected.");
        } catch (Exception e) { ra.addFlashAttribute("error", "❌ " + e.getMessage()); }
        return "redirect:/staff/loans/review?id=" + loanId;
    }
}
