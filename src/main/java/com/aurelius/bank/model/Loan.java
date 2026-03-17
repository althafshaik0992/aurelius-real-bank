package com.aurelius.bank.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "loans")
@Data @NoArgsConstructor @AllArgsConstructor
public class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="loan_number", unique=true, nullable=false)
    private String loanNumber;

    @ManyToOne @JoinColumn(name="client_id")
    private User client;

    @ManyToOne @JoinColumn(name="account_id")
    private Account account;

    @ManyToOne @JoinColumn(name="reviewed_by_id")
    private User reviewedBy;

    @Enumerated(EnumType.STRING) @Column(name="loan_type")
    private LoanType loanType;

    @Column(name="loan_purpose", length=500)
    private String loanPurpose;

    @Column(name="principal_amount", precision=15, scale=2)
    private BigDecimal principalAmount;

    @Column(name="approved_amount", precision=15, scale=2)
    private BigDecimal approvedAmount;

    @Column(name="outstanding_balance", precision=15, scale=2)
    private BigDecimal outstandingBalance;

    @Column(name="interest_rate", precision=5, scale=2)
    private BigDecimal interestRate;

    @Column(name="term_months")
    private Integer termMonths;

    @Column(name="monthly_payment", precision=15, scale=2)
    private BigDecimal monthlyPayment;

    // Financial profile
    @Column(name="annual_income", precision=15, scale=2)
    private BigDecimal annualIncome;

    @Column(name="employment_status", length=50)
    private String employmentStatus;

    @Column(name="employer_name", length=200)
    private String employerName;

    @Column(name="years_employed")
    private Integer yearsEmployed;

    // PERSONAL
    @Column(name="personal_loan_reason", length=100)
    private String personalLoanReason;

    // AUTO
    @Column(name="vehicle_make", length=50)     private String vehicleMake;
    @Column(name="vehicle_model", length=50)    private String vehicleModel;
    @Column(name="vehicle_year")                private Integer vehicleYear;
    @Column(name="vehicle_condition", length=30) private String vehicleCondition;
    @Column(name="vehicle_price", precision=15, scale=2) private BigDecimal vehiclePrice;
    @Column(name="down_payment_auto", precision=15, scale=2) private BigDecimal downPaymentAuto;
    @Column(name="dealer_name", length=200)     private String dealerName;
    @Column(name="vehicle_vin", length=50)      private String vehicleVin;

    // HOME
    @Column(name="property_address", length=300) private String propertyAddress;
    @Column(name="property_type", length=50)     private String propertyType;
    @Column(name="property_value", precision=15, scale=2) private BigDecimal propertyValue;
    @Column(name="down_payment_home", precision=15, scale=2) private BigDecimal downPaymentHome;
    @Column(name="property_use", length=30)      private String propertyUse;
    @Column(name="is_first_time_buyer")          private Boolean firstTimeBuyer = false;
    @Column(name="existing_mortgage")            private Boolean existingMortgage = false;

    // BUSINESS
    @Column(name="business_name", length=200)    private String businessName;
    @Column(name="business_type", length=100)    private String businessType;
    @Column(name="business_ein", length=20)      private String businessEin;
    @Column(name="years_in_business")            private Integer yearsInBusiness;
    @Column(name="annual_revenue", precision=15, scale=2) private BigDecimal annualRevenue;
    @Column(name="business_purpose", length=300) private String businessPurpose;
    @Column(name="num_employees")                private Integer numEmployees;

    // EDUCATION
    @Column(name="institution_name", length=200) private String institutionName;
    @Column(name="degree_program", length=100)   private String degreeProgram;
    @Column(name="field_of_study", length=100)   private String fieldOfStudy;
    @Column(name="enrollment_status", length=30) private String enrollmentStatus;
    @Column(name="graduation_year")              private Integer graduationYear;
    @Column(name="tuition_cost", precision=15, scale=2) private BigDecimal tuitionCost;

    // MEDICAL
    @Column(name="provider_name", length=200)    private String providerName;
    @Column(name="procedure_type", length=200)   private String procedureType;
    @Column(name="estimated_cost", precision=15, scale=2) private BigDecimal estimatedCost;
    @Column(name="insurance_provider", length=100) private String insuranceProvider;
    @Column(name="insurance_coverage", precision=15, scale=2) private BigDecimal insuranceCoverage;
    @Column(name="is_emergency")                 private Boolean isEmergency = false;

    // Status
    @Enumerated(EnumType.STRING) @Column(name="loan_status")
    private LoanStatus loanStatus = LoanStatus.PENDING;

    @Column(name="rejection_reason", length=500) private String rejectionReason;
    @Column(name="review_notes", length=500)     private String reviewNotes;
    @Column(name="applied_at")                   private LocalDateTime appliedAt = LocalDateTime.now();
    @Column(name="reviewed_at")                  private LocalDateTime reviewedAt;
    @Column(name="disbursed_at")                 private LocalDateTime disbursedAt;
    @Column(name="due_date")                     private LocalDate dueDate;
    @Column(name="next_payment_date")            private LocalDate nextPaymentDate;

    @OneToMany(mappedBy="loan", cascade=CascadeType.ALL, fetch=FetchType.LAZY)
    private List<LoanRepayment> repayments;

    public enum LoanType { PERSONAL, HOME, AUTO, BUSINESS, EDUCATION, MEDICAL }
    public enum LoanStatus { PENDING, UNDER_REVIEW, APPROVED, ACTIVE, REJECTED, PAID_OFF, DEFAULTED }

    public BigDecimal getLtvRatio() {
        if (propertyValue != null && propertyValue.compareTo(BigDecimal.ZERO) > 0 && principalAmount != null)
            return principalAmount.divide(propertyValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        return BigDecimal.ZERO;
    }
}
