package com.aurelius.bank.config;

import com.aurelius.bank.model.*;
import com.aurelius.bank.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    @Autowired private UserRepository userRepo;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private SupportTicketRepository ticketRepo;
    @Autowired private ApprovalRepository approvalRepo;
    @Autowired private ClientApplicationRepository appRepo;
    @Autowired private NotificationRepository notifRepo;
    @Autowired private PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        // ALWAYS clean up incomplete applications on every startup
        var incomplete = appRepo.findIncomplete();
        if (!incomplete.isEmpty()) {
            appRepo.deleteAll(incomplete);
            System.out.println("🧹 Cleaned up " + incomplete.size() + " incomplete application(s) on startup.");
        }

        // Guard: skip seeding if data already exists (prevents duplicate key on restart)
        if (userRepo.count() > 0) {
            System.out.println("⚡ Data already exists — skipping seed.");
            return;
        }
        seedStaff();
        seedClients();
        seedTransactions();
        seedTickets();
        seedApprovals();
        seedApplications();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  ✅ Aurelius Bank Ready!");
        System.out.println("  CLIENT  → http://localhost:8080/login");
        System.out.println("           demo@aurelius.com / password123");
        System.out.println("  STAFF   → http://localhost:8080/staff/login");
        System.out.println("           sarah.chen@aurelius-staff.com / staff2026");
        System.out.println("  SETUP   → http://localhost:8080/setup-account");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void seedStaff() {
        List.of(
                new User(null,"alex.rivera@aurelius-staff.com",   encoder.encode("staff2026"),"Alex Rivera",   User.UserRole.SUPPORT_AGENT,      "ARL-00218",null,true),
                new User(null,"sarah.chen@aurelius-staff.com",    encoder.encode("staff2026"),"Sarah Chen",    User.UserRole.BRANCH_MANAGER,     "ARL-00421",null,true),
                new User(null,"mike.torres@aurelius-staff.com",   encoder.encode("staff2026"),"Mike Torres",   User.UserRole.COMPLIANCE_OFFICER, "ARL-00095",null,true),
                new User(null,"victoria.lane@aurelius-staff.com", encoder.encode("staff2026"),"Victoria Lane", User.UserRole.SUPER_ADMIN,        "ARL-00001",null,true)
        ).forEach(userRepo::save);
    }

    private void seedClients() {
        // Demo clients — created by staff (accounts pre-exist, online access granted)
        User james    = save(new User(null,"demo@aurelius.com",         encoder.encode("password123"),"James Whitmore",   User.UserRole.CLIENT,null,"PLATINUM",true));
        User eleanor  = save(new User(null,"eleanor@meridian.com",      encoder.encode("password123"),"Eleanor Ashworth", User.UserRole.CLIENT,null,"PRIVATE", true));
        User priya    = save(new User(null,"priya@nexuscap.com",        encoder.encode("password123"),"Priya Sundaram",   User.UserRole.CLIENT,null,"PRIVATE", true));
        User robert   = save(new User(null,"robert@email.com",          encoder.encode("password123"),"Robert King",      User.UserRole.CLIENT,null,"STANDARD",true));

        // Accounts
        saveAcc("4821", james,   Account.AccountType.CHECKING,     new BigDecimal("182450.32"), "ACTIVE");
        saveAcc("7293", james,   Account.AccountType.SAVINGS,      new BigDecimal("1232092.18"),"ACTIVE");
        saveAcc("0194", james,   Account.AccountType.BROKERAGE,    new BigDecimal("1432850.00"),"ACTIVE");
        saveAcc("9912", james,   Account.AccountType.CREDIT_CARD,  new BigDecimal("-24180.50"), "ACTIVE");
        saveAcc("6610", eleanor, Account.AccountType.CHECKING,     new BigDecimal("3200000.00"),"ACTIVE");
        saveAcc("6611", eleanor, Account.AccountType.SAVINGS,      new BigDecimal("4924000.00"),"ACTIVE");
        saveAcc("9234", priya,   Account.AccountType.CHECKING,     new BigDecimal("850000.00"), "ACTIVE");
        saveAcc("9235", priya,   Account.AccountType.BROKERAGE,    new BigDecimal("4631200.00"),"ACTIVE");
        saveAcc("3312", robert,  Account.AccountType.CHECKING,     new BigDecimal("142800.00"), "FROZEN");

        // Welcome notifications
        notif(james,   "Welcome to Aurelius!", "Your PLATINUM banking portal is ready. All accounts have been verified.", "ACCOUNT","🏦");
        notif(eleanor, "Welcome to Aurelius!", "Your PRIVATE banking portal is ready.", "ACCOUNT","🏦");
        notif(priya,   "Welcome to Aurelius!", "Your PRIVATE banking portal is ready.", "ACCOUNT","🏦");
        notif(robert,  "Account Frozen",       "Your checking account has been temporarily frozen. Please visit your branch.", "ACCOUNT","🔒");
    }

    private User save(User u) { return userRepo.save(u); }

    private void saveAcc(String num, User owner, Account.AccountType type, BigDecimal bal, String status) {
        Account a = new Account();
        a.setAccountNumber(num); a.setOwner(owner); a.setType(type);
        a.setBalance(bal); a.setStatus(status); a.setCreatedAt(LocalDateTime.now().minusMonths(18));
        accountRepo.save(a);
    }

    private void notif(User user, String title, String msg, String type, String icon) {
        Notification n = new Notification();
        n.setUser(user); n.setTitle(title); n.setMessage(msg);
        n.setType(type); n.setIcon(icon); n.setRead(false);
        n.setCreatedAt(LocalDateTime.now().minusDays(1));
        notifRepo.save(n);
    }

    private void seedTransactions() {
        Account checking = accountRepo.findByAccountNumber("4821").get();
        Account savings  = accountRepo.findByAccountNumber("7293").get();
        List.of(
                tx(checking,"Emirates Business Class","Travel",  new BigDecimal("-4280.00"), Transaction.TransactionType.DEBIT,  Transaction.TransactionStatus.COMPLETED,1),
                tx(checking,"Dividend — AAPL",        "Invest",  new BigDecimal("2840.00"),  Transaction.TransactionType.CREDIT, Transaction.TransactionStatus.COMPLETED,2),
                tx(checking,"Property Management",    "Real Est",new BigDecimal("-8500.00"), Transaction.TransactionType.DEBIT,  Transaction.TransactionStatus.PENDING,  3),
                tx(checking,"Wire Transfer Received", "Transfer",new BigDecimal("125000.00"),Transaction.TransactionType.CREDIT, Transaction.TransactionStatus.COMPLETED,4),
                tx(checking,"Nobu NYC",               "Dining",  new BigDecimal("-680.00"),  Transaction.TransactionType.DEBIT,  Transaction.TransactionStatus.COMPLETED,5),
                tx(savings, "NVDA 20 Shares",         "Invest",  new BigDecimal("-28060.00"),Transaction.TransactionType.DEBIT,  Transaction.TransactionStatus.COMPLETED,6),
                tx(checking,"Four Seasons Dubai",     "Travel",  new BigDecimal("-6240.00"), Transaction.TransactionType.DEBIT,  Transaction.TransactionStatus.COMPLETED,7),
                tx(checking,"Salary Deposit",         "Income",  new BigDecimal("48000.00"), Transaction.TransactionType.CREDIT, Transaction.TransactionStatus.COMPLETED,10),
                tx(checking,"Equinox Membership",     "Health",  new BigDecimal("-280.00"),  Transaction.TransactionType.DEBIT,  Transaction.TransactionStatus.COMPLETED,14),
                tx(savings, "Internal Transfer",      "Transfer",new BigDecimal("50000.00"), Transaction.TransactionType.CREDIT, Transaction.TransactionStatus.COMPLETED,15)
        ).forEach(txRepo::save);
    }

    private Transaction tx(Account a,String desc,String cat,BigDecimal amt,
                           Transaction.TransactionType type,Transaction.TransactionStatus status,int daysAgo) {
        Transaction t = new Transaction();
        t.setAccount(a);t.setDescription(desc);t.setCategory(cat);t.setAmount(amt);
        t.setType(type);t.setStatus(status);
        t.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        t.setReferenceId("TXN-"+System.nanoTime());
        return t;
    }

    private void seedTickets() {
        User james   = userRepo.findByEmail("demo@aurelius.com").get();
        User eleanor = userRepo.findByEmail("eleanor@meridian.com").get();
        User priya   = userRepo.findByEmail("priya@nexuscap.com").get();
        User robert  = userRepo.findByEmail("robert@email.com").get();
        User sarah   = userRepo.findByEmail("sarah.chen@aurelius-staff.com").get();
        User mike    = userRepo.findByEmail("mike.torres@aurelius-staff.com").get();
        User alex    = userRepo.findByEmail("alex.rivera@aurelius-staff.com").get();

        // Active call — IN_PROGRESS
        saveTkt("TKT-4821",james,sarah,  "Fraudulent charge — Emirates $4,280",   "Disputed transaction on Platinum Card", SupportTicket.Priority.CRITICAL,SupportTicket.TicketStatus.IN_PROGRESS,"Fraud / Dispute");
        // Escalated — shown in queue
        saveTkt("TKT-4818",james,mike,   "Wire $485,000 compliance hold",          "International wire flagged for review",  SupportTicket.Priority.HIGH,   SupportTicket.TicketStatus.ESCALATED,"Wire Transfer");
        saveTkt("TKT-4815",eleanor,alex, "Account freeze — urgent access needed",  "Client unable to access frozen account", SupportTicket.Priority.HIGH,   SupportTicket.TicketStatus.OPEN,"Account");
        saveTkt("TKT-4812",priya,sarah,  "Wire transfer clarification required",   "Beneficiary details need verification",  SupportTicket.Priority.MEDIUM, SupportTicket.TicketStatus.OPEN,"Wire Transfer");
        saveTkt("TKT-4809",robert,mike,  "Card replacement — physical card lost",  "Client needs new card issued",           SupportTicket.Priority.LOW,    SupportTicket.TicketStatus.OPEN,"Card Services");
        // Resolved
        saveTkt("TKT-4800",james,alex,   "Statement request — Q4 2025",            "Client needed PDF statement",            SupportTicket.Priority.LOW,    SupportTicket.TicketStatus.RESOLVED,"Statements");
        saveTkt("TKT-4795",eleanor,sarah,"Password reset assistance",              "Client locked out of portal",            SupportTicket.Priority.MEDIUM, SupportTicket.TicketStatus.RESOLVED,"Access");
    }

    private void saveTkt(String num,User cust,User staff,String title,String desc,
                         SupportTicket.Priority p,SupportTicket.TicketStatus s,String cat) {
        SupportTicket t=new SupportTicket();
        t.setTicketNumber(num);t.setCustomer(cust);t.setAssignedTo(staff);
        t.setTitle(title);t.setDescription(desc);t.setPriority(p);t.setStatus(s);t.setCategory(cat);
        t.setCreatedAt(LocalDateTime.now().minusHours(6));
        ticketRepo.save(t);
    }

    private void seedApprovals() {
        User james  = userRepo.findByEmail("demo@aurelius.com").get();
        User priya  = userRepo.findByEmail("priya@nexuscap.com").get();
        saveApr(james, "International Wire — Dubai","Beneficiary: Al Futtaim",new BigDecimal("485000"),Approval.ApprovalType.WIRE_TRANSFER,Approval.ApprovalStatus.PENDING,"LARGE_TXN");
        saveApr(priya, "Unusual Cash Withdrawal","3 ATMs in 2 hrs",           new BigDecimal("45000"), Approval.ApprovalType.CASH_WITHDRAWAL,Approval.ApprovalStatus.PENDING,"SUSPICIOUS");
    }

    private void saveApr(User cust,String title,String desc,BigDecimal amt,
                         Approval.ApprovalType type,Approval.ApprovalStatus status,String risk) {
        Approval a=new Approval();
        a.setCustomer(cust);a.setTitle(title);a.setDescription(desc);a.setAmount(amt);
        a.setType(type);a.setStatus(status);a.setRiskFlag(risk);
        a.setCreatedAt(LocalDateTime.now().minusHours(3));
        approvalRepo.save(a);
    }

    private void seedApplications() {
        // No applications seeded — staff creates them via the New Client Application form.
        // This keeps the applications list clean and reflects real banking workflow.
    }

    private void saveApp(User staff, User clientUser, String fullName, String email,
                         String dob, String nat, String phone,
                         String addr1, String addr2, String city, String state, String zip, String country,
                         String idType, String idNum, String idExpiry, String docName,
                         String accType, String tier, ClientApplication.ApplicationStatus status) {
        ClientApplication app = new ClientApplication();
        app.setCreatedByStaff(staff);
        app.setClientUser(clientUser);
        app.setFullName(fullName); app.setEmail(email);
        app.setDateOfBirth(LocalDate.parse(dob)); app.setNationality(nat); app.setPhoneNumber(phone);
        app.setAddressLine1(addr1); app.setAddressLine2(addr2); app.setCity(city);
        app.setState(state); app.setZipCode(zip); app.setCountry(country);
        app.setIdType(idType); app.setIdNumber(idNum); app.setIdExpiry(LocalDate.parse(idExpiry));
        app.setIdDocumentPath("uploads/kyc/" + docName); app.setIdDocumentName(docName);
        app.setAccountType(accType); app.setInitialTier(tier); app.setStatus(status);
        app.setCreatedAt(LocalDateTime.now().minusDays((long)(Math.random()*30+1)));
        if (status == ClientApplication.ApplicationStatus.ACCESS_GRANTED) {
            app.setOnlineAccessGranted(true);
            app.setOnlineAccessGrantedAt(LocalDateTime.now().minusDays(2));
            app.setReviewNotes("All documents verified in person at branch.");
        }
        if (status == ClientApplication.ApplicationStatus.APPROVED) {
            app.setReviewNotes("Documents verified. Pending access grant.");
        }
        appRepo.save(app);
    }
}
