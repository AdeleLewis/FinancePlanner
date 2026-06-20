package com.budget.savings;

import com.budget.transaction.Transaction;
import com.budget.transaction.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SavingsRecommender {

    private static final int LOOKBACK_MONTHS = 3;
    private static final int TOP_CATEGORIES_TO_SUGGEST = 2;
    private static final Set<String> DISCRETIONARY = Set.of("Eating Out", "Entertainment", "Shopping");

    private final TransactionRepository transactionRepository;

    public SavingsRecommender(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Recommendation recommend(BigDecimal currentSavings, BigDecimal targetAmount, LocalDate targetDate) {
        YearMonth now = YearMonth.now();
        YearMonth target = YearMonth.from(targetDate);
        long monthsRemaining = Math.max(1, ChronoUnit.MONTHS.between(now, target));

        BigDecimal gap = targetAmount.subtract(currentSavings).max(BigDecimal.ZERO);
        BigDecimal monthlyNeeded = gap.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.HALF_UP);

        BigDecimal avgMonthlyDisposable = calculateAvgDisposable();
        boolean onTrack = monthlyNeeded.compareTo(avgMonthlyDisposable) <= 0;

        List<String> categoriesToCut = onTrack
                ? List.of()
                : suggestCategoriesToCut();

        return new Recommendation(
                currentSavings,
                targetAmount,
                gap,
                monthsRemaining,
                monthlyNeeded,
                avgMonthlyDisposable,
                onTrack,
                categoriesToCut);
    }

    private BigDecimal calculateAvgDisposable() {
        YearMonth current = YearMonth.from(LocalDate.now());
        YearMonth start = current.minusMonths(LOOKBACK_MONTHS - 1L);
        List<Transaction> transactions = transactionRepository.findByDateBetweenOrderByDateAsc(
                start.atDay(1), current.atEndOfMonth());
        BigDecimal income = transactions.stream()
                .filter(t -> t.getAmount().signum() > 0)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = transactions.stream()
                .filter(t -> t.getAmount().signum() < 0)
                .map(t -> t.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return income.subtract(expenses).divide(BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);
    }

    private List<String> suggestCategoriesToCut() {
        YearMonth lastMonth = YearMonth.from(LocalDate.now()).minusMonths(1);
        List<Transaction> transactions = transactionRepository.findByDateBetweenOrderByDateAsc(
                lastMonth.atDay(1), lastMonth.atEndOfMonth());
        Map<String, BigDecimal> spendByCategory = transactions.stream()
                .filter(t -> t.getAmount().signum() < 0)
                .filter(t -> DISCRETIONARY.contains(t.getCategory()))
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)));
        return spendByCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(TOP_CATEGORIES_TO_SUGGEST)
                .map(Map.Entry::getKey)
                .toList();
    }

    public record Recommendation(
            BigDecimal currentSavings,
            BigDecimal targetAmount,
            BigDecimal gap,
            long monthsRemaining,
            BigDecimal monthlyNeeded,
            BigDecimal avgMonthlyDisposable,
            boolean onTrack,
            List<String> suggestedCategoriesToCut) {
    }
}
