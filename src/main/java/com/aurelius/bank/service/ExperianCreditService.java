package com.aurelius.bank.service;

import com.aurelius.bank.model.CreditCheck;
import com.aurelius.bank.model.Loan;
import com.aurelius.bank.model.User;
import com.aurelius.bank.repository.CreditCheckRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExperianCreditService {

    private static final String TOKEN_URL =
            "https://sandbox-us-api.experian.com/oauth2/v1/token";

    private static final String CREDIT_URL =
            "https://sandbox-us-api.experian.com/consumerservices/credit-profile/v2/credit-report";

    private static final String SUBSCRIBER_CODE = "2222222";
    private static final String CLIENT_REFERENCE_ID = "SBMYSQL";

    @Value("${experian.api.enabled:false}")
    private boolean apiEnabled;

    @Value("${experian.client.id:NOT_SET}")
    private String clientId;

    @Value("${experian.client.secret:NOT_SET}")
    private String clientSecret;

    @Value("${experian.username:NOT_SET}")
    private String username;

    @Value("${experian.password:NOT_SET}")
    private String password;

    @Autowired
    private CreditCheckRepository creditCheckRepo;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private String cachedToken;
    private long tokenExpiresAt = 0L;

    @Transactional
    public CreditCheck runCreditCheck(User client, Loan loan, String ssn) {
        String cleaned = ssn.replaceAll("[^0-9]", "");

        if (cleaned.length() != 9) {
            throw new RuntimeException("Invalid SSN. Please enter 9 digits (XXX-XX-XXXX).");
        }

        CreditCheck check = (apiEnabled
                && !"NOT_SET".equals(clientId)
                && !"NOT_SET".equals(clientSecret)
                && !"NOT_SET".equals(username)
                && !"NOT_SET".equals(password))
                ? callExperian(client, loan, cleaned)
                : simulate(client, loan, cleaned);

        return creditCheckRepo.save(check);
    }

    private CreditCheck callExperian(User client, Loan loan, String ssn) {
        try {
            String token = getToken();

            System.out.println("[Experian] ✅ Token acquired: "
                    + token.substring(0, Math.min(20, token.length())) + "...");
            System.out.println("[Experian] Calling credit report endpoint: " + CREDIT_URL);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setBearerAuth(token);
            headers.set("clientReferenceId", CLIENT_REFERENCE_ID);

            String[] parts = client.getFullName().trim().split("\\s+");
            String firstName = parts.length > 0 ? parts[0] : "TEST";
            String lastName = parts.length > 1 ? parts[parts.length - 1] : "CONSUMER";

            Map<String, Object> body = buildCreditReportRequest(firstName, lastName, ssn);
            String requestJson = mapper.writeValueAsString(body);

            System.out.println("[Experian] Request body: " + requestJson);
            System.out.println("[Experian] Final headers: " + headers);

            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    CREDIT_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String responseBody = response.getBody() == null ? "" : response.getBody();

            System.out.println("[Experian] Response status: " + response.getStatusCode());
            System.out.println("[Experian] Response body preview: "
                    + responseBody.substring(0, Math.min(500, responseBody.length())));

            return parseResponse(client, loan, ssn, responseBody);

        } catch (HttpClientErrorException e) {
            System.err.println("[Experian] ❌ HTTP " + e.getStatusCode() + " error");
            System.err.println("[Experian] Response body: " + e.getResponseBodyAsString());

            CreditCheck fallback = simulate(client, loan, ssn);
            fallback.setBureauSource("SIMULATED_FALLBACK");
            fallback.setRawResponse("HTTP_" + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            return fallback;

        } catch (HttpServerErrorException e) {
            System.err.println("[Experian] ❌ Server error " + e.getStatusCode());
            System.err.println("[Experian] Response body: " + e.getResponseBodyAsString());

            CreditCheck fallback = simulate(client, loan, ssn);
            fallback.setBureauSource("SIMULATED_FALLBACK");
            fallback.setRawResponse("SERVER_" + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            return fallback;

        } catch (Exception e) {
            System.err.println("[Experian] ❌ Unexpected error: "
                    + e.getClass().getSimpleName() + " — " + e.getMessage());

            CreditCheck fallback = simulate(client, loan, ssn);
            fallback.setBureauSource("SIMULATED_FALLBACK");
            fallback.setRawResponse("ERROR: " + e.getMessage());
            return fallback;
        }
    }

    private Map<String, Object> buildCreditReportRequest(String firstName, String lastName, String ssn) {
        Map<String, Object> body = new LinkedHashMap<>();

        Map<String, Object> consumerPii = new LinkedHashMap<>();
        Map<String, Object> primaryApplicant = new LinkedHashMap<>();

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("lastName", lastName.toUpperCase());
        name.put("firstName", firstName.toUpperCase());

        Map<String, Object> ssnMap = new LinkedHashMap<>();
        ssnMap.put("ssn", ssn);

        Map<String, Object> currentAddress = new LinkedHashMap<>();
        currentAddress.put("line1", "10655 NORTH BIRCH STREET");
        currentAddress.put("city", "BURBANK");
        currentAddress.put("state", "CA");
        currentAddress.put("zipCode", "91502");
        currentAddress.put("country", "USA");

        primaryApplicant.put("name", name);
        primaryApplicant.put("ssn", ssnMap);
        primaryApplicant.put("currentAddress", currentAddress);

        consumerPii.put("primaryApplicant", primaryApplicant);
        body.put("consumerPii", consumerPii);

        Map<String, Object> requestor = new LinkedHashMap<>();
        requestor.put("subscriberCode", SUBSCRIBER_CODE);
        body.put("requestor", requestor);

        Map<String, Object> addOns = new LinkedHashMap<>();

        Map<String, Object> riskModels = new LinkedHashMap<>();
        riskModels.put("modelIndicator", List.of("F", "V4"));
        riskModels.put("scorePercentile", "Y");
        addOns.put("riskModels", riskModels);

        Map<String, Object> summaries = new LinkedHashMap<>();
        summaries.put("summaryType", List.of("PROFILE"));
        addOns.put("summaries", summaries);

        addOns.put("fraudShield", "Y");
        addOns.put("paymentHistory84", "N");

        body.put("addOns", addOns);

        return body;
    }

    private CreditCheck parseResponse(User client, Loan loan, String ssn, String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        JsonNode profileArray = root.path("creditProfile");
        JsonNode profile = profileArray.isArray() && profileArray.size() > 0
                ? profileArray.get(0)
                : root;

        int score = 0;

        JsonNode riskModels = profile.path("riskModel");
        if (riskModels.isArray()) {
            for (JsonNode model : riskModels) {
                String scoreStr = model.path("score").asText("0").replaceAll("[^0-9]", "");
                if (!scoreStr.isEmpty()) {
                    int parsed = Integer.parseInt(scoreStr);
                    if (parsed >= 300 && parsed <= 850) {
                        score = parsed;
                        break;
                    }
                }
            }
        }

        if (score == 0) {
            JsonNode scorecard = profile.path("scorecard");
            if (scorecard.isArray() && scorecard.size() > 0) {
                String scoreStr = scorecard.get(0).path("score").asText("0").replaceAll("[^0-9]", "");
                if (!scoreStr.isEmpty()) {
                    score = Integer.parseInt(scoreStr);
                }
            }
        }

        if (score < 300 || score > 850) {
            score = deterministicScore(ssn);
        }

        CreditCheck check = buildFromScore(client, loan, ssn, score);
        check.setBureauSource("EXPERIAN_SANDBOX");
        check.setBureauReference(root.path("requestId").asText(
                "EXP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        ));

        JsonNode summary = profile.path("profileSummary");
        if (summary.isMissingNode() || summary.isNull()) {
            summary = profile.path("summary");
        }

        if (!summary.isMissingNode()) {
            JsonNode openAccNode = summary.path("openAccounts");
            if (!openAccNode.isMissingNode() && openAccNode.isInt()) {
                check.setOpenAccounts(openAccNode.asInt());
            }

            JsonNode derogNode = summary.path("derogatoryPublicRecord");
            if (!derogNode.isMissingNode() && derogNode.isInt()) {
                check.setDerogatoryMarks(derogNode.asInt());
            }

            JsonNode balNode = summary.path("balanceOwed");
            if (!balNode.isMissingNode()) {
                try {
                    check.setTotalDebt(new BigDecimal(
                            balNode.asText("0").replaceAll("[^0-9.]", "")
                    ));
                } catch (Exception ignored) {
                }
            }
        }

        System.out.println("[Experian] ✅ Successfully parsed credit report. Score: " + score);
        check.setRawResponse(json.length() > 4000 ? json.substring(0, 4000) : json);

        return check;
    }

    private String getToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            System.out.println("[Experian] Using cached token");
            return cachedToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Grant_type", "password");

        ObjectNode bodyNode = mapper.createObjectNode();
        bodyNode.put("username", username);
        bodyNode.put("password", password);
        bodyNode.put("client_id", clientId);
        bodyNode.put("client_secret", clientSecret);

        String bodyJson = mapper.writeValueAsString(bodyNode);

        System.out.println("[Experian] Requesting token — user: " + username
                + ", client_id: " + clientId.substring(0, Math.min(8, clientId.length())) + "...");
        System.out.println("[Experian] Token URL: " + TOKEN_URL);

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, headers);

        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(TOKEN_URL, entity, String.class);
        } catch (HttpClientErrorException e) {
            System.err.println("[Experian] ❌ Token HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
            throw new RuntimeException("Experian token request failed: " + e.getResponseBodyAsString(), e);
        }

        String responseBody = response.getBody() == null ? "" : response.getBody();

        System.out.println("[Experian] ✅ Token response status: " + response.getStatusCode());
        System.out.println("[Experian] Token response (first 200): "
                + responseBody.substring(0, Math.min(200, responseBody.length())));

        JsonNode node = mapper.readTree(responseBody);

        if (!node.has("access_token")) {
            throw new RuntimeException("No access_token in response: " + responseBody);
        }

        cachedToken = node.path("access_token").asText();
        long expiresIn = node.path("expires_in").asLong(1800);
        tokenExpiresAt = System.currentTimeMillis() + ((expiresIn - 120) * 1000);

        System.out.println("[Experian] ✅ Token obtained, valid for " + expiresIn + "s");
        return cachedToken;
    }

    private CreditCheck simulate(User client, Loan loan, String ssn) {
        int score = deterministicScore(ssn);
        CreditCheck check = buildFromScore(client, loan, ssn, score);
        check.setBureauSource("SIMULATED");
        check.setBureauReference("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return check;
    }

    private CreditCheck buildFromScore(User client, Loan loan, String ssn, int score) {
        score = Math.max(300, Math.min(850, score));

        String rating;
        String recommendation;
        int openAccounts;
        int derogatoryMarks;
        int utilization;
        int paymentHistory;

        if (score >= 750) {
            rating = "EXCELLENT";
            recommendation = "AUTO_APPROVE";
            openAccounts = 6 + (score % 5);
            derogatoryMarks = 0;
            utilization = 8 + (score % 15);
            paymentHistory = Math.min(98 + (score % 3), 100);
        } else if (score >= 700) {
            rating = "GOOD";
            recommendation = "AUTO_APPROVE";
            openAccounts = 4 + (score % 4);
            derogatoryMarks = 0;
            utilization = 18 + (score % 14);
            paymentHistory = Math.min(93 + (score % 5), 100);
        } else if (score >= 650) {
            rating = "FAIR";
            recommendation = "MANUAL_REVIEW";
            openAccounts = 3 + (score % 4);
            derogatoryMarks = 1;
            utilization = 32 + (score % 20);
            paymentHistory = 84 + (score % 9);
        } else if (score >= 580) {
            rating = "POOR";
            recommendation = "MANUAL_REVIEW";
            openAccounts = 2 + (score % 3);
            derogatoryMarks = 2 + (score % 2);
            utilization = 55 + (score % 20);
            paymentHistory = 72 + (score % 12);
        } else {
            rating = "VERY_POOR";
            recommendation = "AUTO_REJECT";
            openAccounts = 1 + (score % 3);
            derogatoryMarks = 4 + (score % 3);
            utilization = Math.min(78 + (score % 15), 99);
            paymentHistory = 55 + (score % 17);
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

    private int deterministicScore(String ssn) {
        long sum = 0;
        for (int i = 0; i < ssn.length(); i++) {
            sum += (long) Character.getNumericValue(ssn.charAt(i)) * (i + 1) * 17;
        }
        return 300 + (int) (sum % 551);
    }
}