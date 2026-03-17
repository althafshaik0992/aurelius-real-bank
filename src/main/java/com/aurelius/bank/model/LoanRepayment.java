package com.aurelius.bank.model;
import jakarta.persistence.*;
import lombok.Data; import lombok.NoArgsConstructor; import lombok.AllArgsConstructor;
import java.math.BigDecimal; import java.time.LocalDate; import java.time.LocalDateTime;

@Entity @Table(name="loan_repayments")
@Data @NoArgsConstructor @AllArgsConstructor
public class LoanRepayment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="loan_id") private Loan loan;
    @Column(name="installment_number")   private Integer installmentNumber;
    @Column(name="due_date")             private LocalDate dueDate;
    @Column(name="paid_date")            private LocalDateTime paidDate;
    @Column(name="amount_due", precision=15, scale=2)         private BigDecimal amountDue;
    @Column(name="principal_component", precision=15, scale=2) private BigDecimal principalComponent;
    @Column(name="interest_component", precision=15, scale=2)  private BigDecimal interestComponent;
    @Column(name="amount_paid", precision=15, scale=2)         private BigDecimal amountPaid;
    @Enumerated(EnumType.STRING) @Column(name="repayment_status")
    private RepaymentStatus repaymentStatus = RepaymentStatus.UPCOMING;
    public enum RepaymentStatus { UPCOMING, PAID, OVERDUE, PARTIAL }
}
