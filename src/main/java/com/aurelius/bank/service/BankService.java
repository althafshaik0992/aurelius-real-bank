package com.aurelius.bank.service;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class BankService {

    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private NotificationRepository notifRepo;

    @Transactional
    public void transfer(String fromAccNum, String toAccNum, BigDecimal amount, String note) {
        Account from = accountRepo.findByAccountNumber(fromAccNum)
            .orElseThrow(() -> new RuntimeException("Source account not found."));
        Account to = accountRepo.findByAccountNumber(toAccNum)
            .orElseThrow(() -> new RuntimeException("Destination account not found."));

        if ("FROZEN".equals(from.getStatus()))
            throw new RuntimeException("Source account is frozen.");
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Amount must be greater than zero.");
        if (from.getBalance().compareTo(amount) < 0)
            throw new RuntimeException("Insufficient funds. Available: $" +
                String.format("%,.2f", from.getBalance()));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepo.save(from); accountRepo.save(to);

        String ref = "TRF-" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
        String desc = (note != null && !note.isBlank()) ? note : "Transfer";

        Transaction debit = new Transaction();
        debit.setAccount(from); debit.setDescription(desc + " → •••• " + toAccNum);
        debit.setCategory("Transfer"); debit.setAmount(amount.negate());
        debit.setType(Transaction.TransactionType.DEBIT);
        debit.setStatus(Transaction.TransactionStatus.COMPLETED);
        debit.setCreatedAt(LocalDateTime.now()); debit.setReferenceId(ref);
        txRepo.save(debit);

        Transaction credit = new Transaction();
        credit.setAccount(to); credit.setDescription(desc + " ← •••• " + fromAccNum);
        credit.setCategory("Transfer"); credit.setAmount(amount);
        credit.setType(Transaction.TransactionType.CREDIT);
        credit.setStatus(Transaction.TransactionStatus.COMPLETED);
        credit.setCreatedAt(LocalDateTime.now()); credit.setReferenceId(ref);
        txRepo.save(credit);

        // Notify sender
        Notification n = new Notification();
        n.setUser(from.getOwner());
        n.setTitle("Transfer Completed");
        n.setMessage(String.format("$%,.2f transferred from account •••• %s. Ref: %s", amount, fromAccNum, ref));
        n.setType("TRANSACTION"); n.setIcon("↕"); n.setCreatedAt(LocalDateTime.now());
        notifRepo.save(n);
    }
}
