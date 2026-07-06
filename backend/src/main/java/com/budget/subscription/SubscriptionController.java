package com.budget.subscription;

import com.budget.transaction.Transaction;
import com.budget.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private static final int LOOKBACK_MONTHS = 12;

    /** Matches the {@code merchant_key} column length; longer keys would 500 on the DB constraint. */
    private static final int MAX_MERCHANT_KEY_LENGTH = 128;

    private final TransactionRepository transactionRepository;
    private final SubscriptionDecisionRepository decisionRepository;

    public SubscriptionController(TransactionRepository transactionRepository,
                                  SubscriptionDecisionRepository decisionRepository) {
        this.transactionRepository = transactionRepository;
        this.decisionRepository = decisionRepository;
    }

    @GetMapping
    public List<SubscriptionView> list() {
        final LocalDate today = LocalDate.now();
        final List<Transaction> transactions =
                transactionRepository.findByDateBetweenOrderByDateAsc(today.minusMonths(LOOKBACK_MONTHS), today);
        final Map<String, Decision> decisions = decisionRepository.findAll().stream()
                .collect(Collectors.toMap(SubscriptionDecision::getMerchantKey, SubscriptionDecision::getDecision));
        return SubscriptionDetector.detect(transactions, today).stream()
                .map(detected -> new SubscriptionView(
                        detected.merchantKey(),
                        detected.name(),
                        detected.category(),
                        detected.monthlyAmount(),
                        detected.lastChargedOn(),
                        detected.nextExpectedOn(),
                        detected.timesCharged(),
                        decisions.containsKey(detected.merchantKey())
                                ? decisions.get(detected.merchantKey()).name()
                                : "UNDECIDED"))
                .toList();
    }

    @PutMapping("/decision")
    @Transactional
    public void decide(@RequestBody DecisionRequest request) {
        if (request.merchantKey() == null || request.merchantKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "merchantKey is required");
        }
        if (request.merchantKey().length() > MAX_MERCHANT_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "merchantKey must be at most " + MAX_MERCHANT_KEY_LENGTH + " characters");
        }
        final String choice = request.decision();
        if ("UNDECIDED".equals(choice)) {
            decisionRepository.deleteByMerchantKey(request.merchantKey());
            return;
        }
        final Decision decision;
        try {
            decision = Decision.valueOf(choice == null ? "" : choice);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "decision must be KEEP, CANCEL or UNDECIDED");
        }
        decisionRepository.findByMerchantKey(request.merchantKey())
                .ifPresentOrElse(
                        existing -> existing.setDecision(decision),
                        () -> decisionRepository.save(new SubscriptionDecision(request.merchantKey(), decision)));
    }

    public record SubscriptionView(
            String merchantKey,
            String name,
            String category,
            BigDecimal monthlyAmount,
            LocalDate lastChargedOn,
            LocalDate nextExpectedOn,
            int timesCharged,
            String decision) {
    }

    public record DecisionRequest(String merchantKey, String decision) {
    }
}
