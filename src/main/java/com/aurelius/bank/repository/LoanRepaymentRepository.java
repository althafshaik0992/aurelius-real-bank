package com.aurelius.bank.repository;
import com.aurelius.bank.model.*; import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository; import java.util.List;
@Repository public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, Long> {
    List<LoanRepayment> findByLoanOrderByInstallmentNumber(Loan l);
}
