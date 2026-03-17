package com.aurelius.bank.model;
import jakarta.persistence.*;
import lombok.Data; import lombok.NoArgsConstructor; import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="credit_checks")
@Data @NoArgsConstructor @AllArgsConstructor
public class CreditCheck {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="client_id")  private User client;
    @OneToOne  @JoinColumn(name="loan_id")    private Loan loan;

    @Column(name="ssn_last4")             private String ssnLast4;
    @Column(name="credit_score")          private Integer creditScore;
    @Column(name="credit_rating")         private String creditRating;
    @Column(name="bureau_reference")      private String bureauReference;
    @Column(name="bureau_source")         private String bureauSource;   // EXPERIAN_SANDBOX / SIMULATED
    @Column(name="checked_at")            private LocalDateTime checkedAt = LocalDateTime.now();
    @Column(name="recommendation")        private String recommendation;

    // Bureau data
    @Column(name="open_accounts")         private Integer openAccounts;
    @Column(name="derogatory_marks")      private Integer derogatoryMarks;
    @Column(name="credit_utilization")    private Integer creditUtilization;
    @Column(name="payment_history_pct")   private Integer paymentHistoryPct;
    @Column(name="total_debt", precision=15, scale=2)
    private java.math.BigDecimal totalDebt;
    @Column(name="raw_response", length=4000)
    private String rawResponse;            // store raw JSON for audit
}
