package com.aurelius.bank.repository;
import com.aurelius.bank.model.*; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository; import java.util.Optional;
@Repository public interface CreditCheckRepository extends JpaRepository<CreditCheck, Long> {
    Optional<CreditCheck> findByLoan(Loan l);
    Optional<CreditCheck> findTopByClientOrderByCheckedAtDesc(User c);
}
