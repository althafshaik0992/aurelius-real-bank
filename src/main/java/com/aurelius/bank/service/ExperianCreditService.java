package com.aurelius.bank.service;

import com.aurelius.bank.model.CreditCheck;
import com.aurelius.bank.model.Loan;
import com.aurelius.bank.model.User;
import com.aurelius.bank.repository.CreditCheckRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Experian Sandbox Credit Check Service
 *
 * SANDBOX SETUP:
 * 1. Sign up free at https://developer.experian.com
 * 2. Create an app → get client_id and client_secret
 * 3. Add to application.properties:
 *    experian.client.id=YOUR_CLIENT_ID
 *    experian.client.secret=YOUR_CLIENT_SECRET
 *    experian.api.enabled=true
 *
 * SANDBOX TEST SSNs (Experian provided):
 *   999-99-9999  → Excellent credit (~800)
 *   999-99-9990  → Good credit (~720)
 *   999-99-9980  → Fair credit (~660)
 *   999-99-9970  → Poor credit (~600)
 *   999-99-9960  → Very poor (~520)
 *
 * When experian.api.enabled=false (default), falls back to deterministic simulation.
 */
@Service
public class ExperianCreditService {

    // ── Experian API endpoints ─────────────────────────────────────────────────
    private static final String SANDBOX_BASE     = "https://sandbox.experian.com";
    private static final String AUTH_URL         = SANDBOX_BASE + "/connect/token";
    private static final String CREDIT_SCORE_URL = SANDBOX_BASE + "/consumerservices/credit-profile/v2/credit-report";

    @Value("${experian.client.id:NOT_CONFIGURED}")
    private String clientId;

    @Value("${experian.client.secret:NOT_CONFIGURED}")
    private String clientSecret;

    @Value("${experian.api.enabled:false}")
    private boolean apiEnabled;

    @Autowired private CreditCheckRepository creditCheckRepo;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── Main entry point ───────────────────────────────────────────────────────
    @Transactional
    public CreditCheck runCreditCheck(User client, Loan loan, String ssn) {
        String cleaned = ssn.replaceAll("[^0-9]", "");
        if (cleaned.length() != 9)
            throw new RuntimeException("Invalid SSN format. Please enter 9 digits (XXX-XX-XXXX).");

        CreditCheck check;
        if (apiEnabled && !"NOT_CONFIGURED".equals(clientId)) {
            check = callExperianSandbox(client, loan, cleaned);
        } else {
            check = runSimulation(client, loan, cleaned);
        }
        return creditCheckRepo.save(check);
    }

    // ── Experian Sandbox API call ──────────────────────────────────────────────
    private CreditCheck callExperianSandbox(User client, Loan loan, String ssn) {
        try {
            // Step 1: Get OAuth2 token
            String token = getExperianToken();

            // Step 2: Call credit report endpoint
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            headers.set("clientReferenceId", loan.getLoanNumber());

            // Build request body per Experian sandbox spec
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> primaryApplicant = new HashMap<>();
            Map<String, Object> personNameType = new HashMap<>();
            personNameType.put("firstName", client.getFullName().split(" ")[0]);
            personNameType.put("lastName",  client.getFullName().split(" ").length > 1
                ? client.getFullName().split(" ")[client.getFullName().split(" ").length - 1] : "N/A");
            primaryApplicant.put("name", personNameType);
            primaryApplicant.put("ssn", ssn);
            requestBody.put("primaryApplicant", primaryApplicant);
            requestBody.put("requestedAttributes", new String[]{
                "CreditScore", "CreditRating", "PaymentHistory",
                "AccountSummary", "DerogatoryInfo", "CreditUtilization"
            });

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(CREDIT_SCORE_URL, entity, String.class);

            return parseExperianResponse(client, loan, ssn, response.getBody());

        } catch (Exception e) {
            // Graceful fallback to simulation if API call fails
            System.err.println("[Experian] API call failed: " + e.getMessage() + " — falling back to simulation");
            CreditCheck check = runSimulation(client, loan, ssn);
            check.setBureauSource("SIMULATED_FALLBACK");
            return check;
        }
    }

    // ── Parse Experian JSON response ───────────────────────────────────────────
    private CreditCheck parseExperianResponse(User client, Loan loan, String ssn, String responseBody) throws Exception {
        JsonNode root = mapper.readTree(responseBody);

        // Navigate Experian response structure
        JsonNode creditProfile = root.path("creditProfile").path(0);
        JsonNode scoreFactors  = creditProfile.path("scorecard");
        JsonNode summary       = creditProfile.path("summary");

        int score = scoreFactors.path("score").asInt(650);

        CreditCheck check = buildCheckFromScore(client, loan, ssn, score);
        check.setBureauSource("EXPERIAN_SANDBOX");
        check.setBureauReference(root.path("requestId").asText(
            "EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));

        // Override with real Experian values if present
        if (!summary.isMissingNode()) {
            if (summary.has("openAccounts"))
                check.setOpenAccounts(summary.path("openAccounts").asInt());
            if (summary.has("derogatoryCount"))
                check.setDerogatoryMarks(summary.path("derogatoryCount").asInt());
            if (summary.has("balances") && summary.path("balances").has("totalDebt"))
                check.setTotalDebt(new BigDecimal(summary.path("balances").path("totalDebt").asText("0")));
        }

        // Store raw response for audit trail
        check.setRawResponse(responseBody.length() > 4000 ? responseBody.substring(0, 4000) : responseBody);
        return check;
    }

    // ── Get Experian OAuth2 token ──────────────────────────────────────────────
    private String getExperianToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "client_id=" + clientId +
                      "&client_secret=" + clientSecret +
                      "&grant_type=client_credentials";

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(AUTH_URL, entity, String.class);

        try {
            JsonNode node = mapper.readTree(response.getBody());
            return node.path("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Experian token: " + e.getMessage());
        }
    }

    // ── Simulation (fallback / dev mode) ──────────────────────────────────────
    /**
     * Deterministic score from SSN — same SSN always returns same score.
     * Covers full 300–850 FICO range realistically.
     */
    private CreditCheck runSimulation(User client, Loan loan, String ssn) {
        int score = generateScoreFromSsn(ssn);
        CreditCheck check = buildCheckFromScore(client, loan, ssn, score);
        check.setBureauSource("SIMULATED");
        check.setBureauReference("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return check;
    }

    // ── Build CreditCheck from score ──────────────────────────────────────────
    private CreditCheck buildCheckFromScore(User client, Loan loan, String ssn, int score) {
        // Clamp to valid FICO range
        score = Math.max(300, Math.min(850, score));

        String rating;
        String recommendation;
        int openAccounts, derogatoryMarks, utilization, paymentHistory;

        if (score >= 750) {
            rating = "EXCELLENT"; recommendation = "AUTO_APPROVE";
            openAccounts = 6 + (score % 5); derogatoryMarks = 0;
            utilization = 8 + (score % 15); paymentHistory = Math.min(98 + (score % 3), 100);
        } else if (score >= 700) {
            rating = "GOOD"; recommendation = "AUTO_APPROVE";
            openAccounts = 4 + (score % 4); derogatoryMarks = 0;
            utilization = 18 + (score % 14); paymentHistory = Math.min(93 + (score % 5), 100);
        } else if (score >= 650) {
            rating = "FAIR"; recommendation = "MANUAL_REVIEW";
            openAccounts = 3 + (score % 4); derogatoryMarks = 1;
            utilization = 32 + (score % 20); paymentHistory = 84 + (score % 9);
        } else if (score >= 580) {
            rating = "POOR"; recommendation = "MANUAL_REVIEW";
            openAccounts = 2 + (score % 3); derogatoryMarks = 2 + (score % 2);
            utilization = 55 + (score % 20); paymentHistory = 72 + (score % 12);
        } else {
            rating = "VERY_POOR"; recommendation = "AUTO_REJECT";
            openAccounts = 1 + (score % 3); derogatoryMarks = 4 + (score % 3);
            utilization = Math.min(78 + (score % 15), 99); paymentHistory = 55 + (score % 17);
        }

        CreditCheck check = new CreditCheck();
        check.setClient(client);
        check.setLoan(loan);
        check.setSsnLast4(ssn.substring(5));
        check.setCreditScore(score);
        check.setCreditRating(rating);
        check.setRecommendation(recommendation);
        check.setCheckedAt(LocalDateTime.now());
        check.setOpenAccounts(openAccounts);
        check.setDerogatoryMarks(derogatoryMarks);
        check.setCreditUtilization(Math.min(utilization, 99));
        check.setPaymentHistoryPct(Math.min(paymentHistory, 100));
        return check;
    }

    // ── Deterministic score from SSN digits ───────────────────────────────────
    private int generateScoreFromSsn(String ssn) {
        long sum = 0;
        for (int i = 0; i < ssn.length(); i++)
            sum += (long) Character.getNumericValue(ssn.charAt(i)) * (i + 1) * 17;
        return 300 + (int)(sum % 551);  // 300–850 range
    }
}
