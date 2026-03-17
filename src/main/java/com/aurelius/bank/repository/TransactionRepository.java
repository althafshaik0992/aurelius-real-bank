package com.aurelius.bank.repository;

import com.aurelius.bank.model.Account;
import com.aurelius.bank.model.Transaction;
import com.aurelius.bank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountOrderByCreatedAtDesc(Account account);

    List<Transaction> findByAccount_OwnerOrderByCreatedAtDesc(User owner);

    List<Transaction> findByFlaggedTrue();
}
