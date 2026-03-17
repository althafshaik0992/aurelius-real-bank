package com.aurelius.bank.service;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LoanService {

    @Autowired private LoanRepository loanRepo;
    @Autowired private LoanRepaymentRepository repaymentRepo;
    @Autowired private ExperianCreditService creditService;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;

    public BigDecimal getInterestRate(Loan.LoanType type, String tier) {
        double base = switch (type) {
            case PERSONAL  -> 12.5;
            case HOME      -> 7.5;
            case AUTO      -> 9.0;
            case BUSINESS  -> 11.0;
            case EDUCATION -> 8.5;
            case MEDICAL   -> 10.0;
        };
        double discount = switch (tier != null ? tier : "STANDARD") {
            case "PLATINUM" -> 1.5;
            case "PRIVATE"  -> 0.75;
            default         -> 0.0;
        };
        return BigDecimal.valueOf(base - discount).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Loan applyForLoan(User client, Long accountId, String loanTypeStr,
                              BigDecimal amount, Integer termMonths, String ssn,
                              Map<String, String> fields) {
        Account account = accountRepo.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found."));
        if (!account.getOwner().getId().equals(client.getId()))
            throw new RuntimeException("Account does not belong to you.");
        if ("FROZEN".equals(account.getStatus()))
            throw new RuntimeException("Selected account is frozen.");
        if (amount.compareTo(BigDecimal.valueOf(1000)) < 0)
            throw new RuntimeException("Minimum loan amount is $1,000.");
        if (amount.compareTo(BigDecimal.valueOf(10_000_000)) > 0)
            throw new RuntimeException("Maximum loan amount is $10,000,000.");

        Loan.LoanType loanType = Loan.LoanType.valueOf(loanTypeStr.toUpperCase());
        BigDecimal rate = getInterestRate(loanType, client.getClientTier());

        Loan loan = new Loan();
        loan.setLoanNumber("LN-" + UUID.randomUUID().toString().substring(0,8).toUpperCase());
        loan.setClient(client);
        loan.setAccount(account);
        loan.setLoanType(loanType);
        loan.setPrincipalAmount(amount);
        loan.setInterestRate(rate);
        loan.setTermMonths(termMonths);
        loan.setMonthlyPayment(calculateEMI(amount, rate, termMonths));
        loan.setLoanStatus(Loan.LoanStatus.PENDING);
        loan.setAppliedAt(LocalDateTime.now());

        // Common
        if (notEmpty(fields, "annualIncome"))   loan.setAnnualIncome(new BigDecimal(fields.get("annualIncome")));
        loan.setEmploymentStatus(fields.get("employmentStatus"));
        loan.setEmployerName(fields.get("employerName"));
        if (notEmpty(fields, "yearsEmployed"))  loan.setYearsEmployed(Integer.parseInt(fields.get("yearsEmployed")));

        switch (loanType) {
            case PERSONAL -> {
                loan.setPersonalLoanReason(fields.get("personalLoanReason"));
                loan.setLoanPurpose(fields.getOrDefault("loanPurpose", fields.getOrDefault("personalLoanReason", "Personal")));
            }
            case AUTO -> {
                loan.setVehicleMake(fields.get("vehicleMake"));
                loan.setVehicleModel(fields.get("vehicleModel"));
                if (notEmpty(fields,"vehicleYear"))     loan.setVehicleYear(Integer.parseInt(fields.get("vehicleYear")));
                loan.setVehicleCondition(fields.get("vehicleCondition"));
                if (notEmpty(fields,"vehiclePrice"))    loan.setVehiclePrice(new BigDecimal(fields.get("vehiclePrice")));
                if (notEmpty(fields,"downPaymentAuto")) loan.setDownPaymentAuto(new BigDecimal(fields.get("downPaymentAuto")));
                loan.setDealerName(fields.get("dealerName"));
                loan.setVehicleVin(fields.get("vehicleVin"));
                loan.setLoanPurpose(fields.getOrDefault("vehicleYear","") + " " + fields.getOrDefault("vehicleMake","") + " " + fields.getOrDefault("vehicleModel",""));
            }
            case HOME -> {
                loan.setPropertyAddress(fields.get("propertyAddress"));
                loan.setPropertyType(fields.get("propertyType"));
                if (notEmpty(fields,"propertyValue"))   loan.setPropertyValue(new BigDecimal(fields.get("propertyValue")));
                if (notEmpty(fields,"downPaymentHome")) loan.setDownPaymentHome(new BigDecimal(fields.get("downPaymentHome")));
                loan.setPropertyUse(fields.get("propertyUse"));
                loan.setFirstTimeBuyer("true".equals(fields.get("firstTimeBuyer")));
                loan.setExistingMortgage("true".equals(fields.get("existingMortgage")));
                loan.setLoanPurpose(fields.getOrDefault("propertyAddress","Home loan"));
            }
            case BUSINESS -> {
                loan.setBusinessName(fields.get("businessName"));
                loan.setBusinessType(fields.get("businessType"));
                loan.setBusinessEin(fields.get("businessEin"));
                if (notEmpty(fields,"yearsInBusiness")) loan.setYearsInBusiness(Integer.parseInt(fields.get("yearsInBusiness")));
                if (notEmpty(fields,"annualRevenue"))   loan.setAnnualRevenue(new BigDecimal(fields.get("annualRevenue")));
                loan.setBusinessPurpose(fields.get("businessPurpose"));
                if (notEmpty(fields,"numEmployees"))    loan.setNumEmployees(Integer.parseInt(fields.get("numEmployees")));
                loan.setLoanPurpose(fields.getOrDefault("businessPurpose","Business") + " — " + fields.getOrDefault("businessName",""));
            }
            case EDUCATION -> {
                loan.setInstitutionName(fields.get("institutionName"));
                loan.setDegreeProgram(fields.get("degreeProgram"));
                loan.setFieldOfStudy(fields.get("fieldOfStudy"));
                loan.setEnrollmentStatus(fields.get("enrollmentStatus"));
                if (notEmpty(fields,"graduationYear"))  loan.setGraduationYear(Integer.parseInt(fields.get("graduationYear")));
                if (notEmpty(fields,"tuitionCost"))     loan.setTuitionCost(new BigDecimal(fields.get("tuitionCost")));
                loan.setLoanPurpose(fields.getOrDefault("degreeProgram","") + " at " + fields.getOrDefault("institutionName",""));
            }
            case MEDICAL -> {
                loan.setProviderName(fields.get("providerName"));
                loan.setProcedureType(fields.get("procedureType"));
                if (notEmpty(fields,"estimatedCost"))      loan.setEstimatedCost(new BigDecimal(fields.get("estimatedCost")));
                loan.setInsuranceProvider(fields.get("insuranceProvider"));
                if (notEmpty(fields,"insuranceCoverage"))  loan.setInsuranceCoverage(new BigDecimal(fields.get("insuranceCoverage")));
                loan.setIsEmergency("true".equals(fields.get("isEmergency")));
                loan.setLoanPurpose(fields.getOrDefault("procedureType","Medical") + " at " + fields.getOrDefault("providerName",""));
            }
        }

        loan = loanRepo.save(loan);
        creditService.runCreditCheck(client, loan, ssn);
        return loan;
    }

    @Transactional
    public Loan approveLoan(Long loanId, User staff, BigDecimal approvedAmount, String notes) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        BigDecimal finalAmount = (approvedAmount != null) ? approvedAmount : loan.getPrincipalAmount();

        loan.setApprovedAmount(finalAmount);
        loan.setOutstandingBalance(finalAmount);
        loan.setMonthlyPayment(calculateEMI(finalAmount, loan.getInterestRate(), loan.getTermMonths()));
        loan.setLoanStatus(Loan.LoanStatus.ACTIVE);
        loan.setReviewedBy(staff);
        loan.setReviewedAt(LocalDateTime.now());
        loan.setDisbursedAt(LocalDateTime.now());
        loan.setReviewNotes(notes);

        LocalDate firstPayment = LocalDate.now().plusMonths(1);
        loan.setNextPaymentDate(firstPayment);
        loan.setDueDate(firstPayment.plusMonths(loan.getTermMonths() - 1));
        loan = loanRepo.save(loan);

        // Credit to account
        Account acc = loan.getAccount();
        acc.setBalance(acc.getBalance().add(finalAmount));
        accountRepo.save(acc);

        // Disbursement transaction
        Transaction tx = new Transaction();
        tx.setAccount(acc);
        tx.setDescription("Loan Disbursement — " + loan.getLoanNumber() + " (" + loan.getLoanType().name().replace("_"," ") + ")");
        tx.setCategory("Loan");
        tx.setAmount(finalAmount);
        tx.setType(Transaction.TransactionType.CREDIT);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setReferenceId(loan.getLoanNumber());
        txRepo.save(tx);

        generateRepaymentSchedule(loan);
        return loan;
    }

    @Transactional
    public Loan rejectLoan(Long loanId, User staff, String reason) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        loan.setLoanStatus(Loan.LoanStatus.REJECTED);
        loan.setReviewedBy(staff);
        loan.setReviewedAt(LocalDateTime.now());
        loan.setRejectionReason(reason);
        return loanRepo.save(loan);
    }

    @Transactional
    public LoanRepayment makeRepayment(Long loanId, User client) {
        Loan loan = loanRepo.findById(loanId).orElseThrow();
        if (!loan.getClient().getId().equals(client.getId())) throw new RuntimeException("Unauthorized.");
        if (loan.getLoanStatus() != Loan.LoanStatus.ACTIVE) throw new RuntimeException("Loan is not active.");

        List<LoanRepayment> schedule = repaymentRepo.findByLoanOrderByInstallmentNumber(loan);
        LoanRepayment next = schedule.stream()
            .filter(r -> r.getRepaymentStatus() == LoanRepayment.RepaymentStatus.UPCOMING ||
                         r.getRepaymentStatus() == LoanRepayment.RepaymentStatus.OVERDUE)
            .findFirst().orElseThrow(() -> new RuntimeException("No outstanding installments."));

        Account acc = loan.getAccount();
        if (acc.getBalance().compareTo(next.getAmountDue()) < 0)
            throw new RuntimeException("Insufficient funds. Required: $" + next.getAmountDue().setScale(2));

        acc.setBalance(acc.getBalance().subtract(next.getAmountDue()));
        accountRepo.save(acc);

        Transaction tx = new Transaction();
        tx.setAccount(acc);
        tx.setDescription("Loan Repayment — " + loan.getLoanNumber() + " (EMI " + next.getInstallmentNumber() + "/" + loan.getTermMonths() + ")");
        tx.setCategory("Loan Repayment");
        tx.setAmount(next.getAmountDue().negate());
        tx.setType(Transaction.TransactionType.DEBIT);
        tx.setStatus(Transaction.TransactionStatus.COMPLETED);
        tx.setCreatedAt(LocalDateTime.now());
        tx.setReferenceId(loan.getLoanNumber() + "-R" + next.getInstallmentNumber());
        txRepo.save(tx);

        next.setRepaymentStatus(LoanRepayment.RepaymentStatus.PAID);
        next.setPaidDate(LocalDateTime.now());
        next.setAmountPaid(next.getAmountDue());
        repaymentRepo.save(next);

        loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(next.getPrincipalComponent()).max(BigDecimal.ZERO));
        schedule.stream()
            .filter(r -> r.getRepaymentStatus() == LoanRepayment.RepaymentStatus.UPCOMING)
            .findFirst()
            .ifPresentOrElse(
                n -> loan.setNextPaymentDate(n.getDueDate()),
                () -> { loan.setLoanStatus(Loan.LoanStatus.PAID_OFF); loan.setOutstandingBalance(BigDecimal.ZERO); loan.setNextPaymentDate(null); }
            );
        loanRepo.save(loan);
        return next;
    }

    public BigDecimal calculateEMI(BigDecimal principal, BigDecimal annualRate, int months) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0)
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal r = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal pow = r.add(BigDecimal.ONE).pow(months, new MathContext(10));
        return principal.multiply(r).multiply(pow)
            .divide(pow.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    private void generateRepaymentSchedule(Loan loan) {
        BigDecimal r = loan.getInterestRate().divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        BigDecimal balance = loan.getApprovedAmount();
        List<LoanRepayment> schedule = new ArrayList<>();
        for (int i = 1; i <= loan.getTermMonths(); i++) {
            BigDecimal interest = balance.multiply(r).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principal = i == loan.getTermMonths() ? balance : loan.getMonthlyPayment().subtract(interest);
            balance = balance.subtract(principal).max(BigDecimal.ZERO);
            LoanRepayment rep = new LoanRepayment();
            rep.setLoan(loan); rep.setInstallmentNumber(i);
            rep.setDueDate(LocalDate.now().plusMonths(i));
            rep.setAmountDue(loan.getMonthlyPayment());
            rep.setPrincipalComponent(principal); rep.setInterestComponent(interest);
            rep.setRepaymentStatus(LoanRepayment.RepaymentStatus.UPCOMING);
            schedule.add(rep);
        }
        repaymentRepo.saveAll(schedule);
    }

    private boolean notEmpty(Map<String,String> m, String key) {
        return m.containsKey(key) && m.get(key) != null && !m.get(key).trim().isEmpty();
    }
}
