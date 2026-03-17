package com.aurelius.bank.repository;

import com.aurelius.bank.model.Account;
import com.aurelius.bank.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByOwner(User owner);

    Optional<Account> findByAccountNumber(String accountNumber);
}
