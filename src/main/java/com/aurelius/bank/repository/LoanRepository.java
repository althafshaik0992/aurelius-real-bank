package com.aurelius.bank.repository;
import com.aurelius.bank.model.*; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository; import java.util.*; 
@Repository public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByClientOrderByAppliedAtDesc(User c);
    List<Loan> findAllByOrderByAppliedAtDesc();
    List<Loan> findByLoanStatus(Loan.LoanStatus s);
    Optional<Loan> findByLoanNumber(String n);
    long countByLoanStatus(Loan.LoanStatus s);
}
