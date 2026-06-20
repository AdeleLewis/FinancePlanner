package com.budget.insight;

import com.budget.transaction.Transaction;
import com.budget.transaction.TransactionRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final TransactionRepository transactionRepository;

    public InsightController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping("/spending-by-category")
    public List<CategorySpending> spendingByCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveTo = to != null ? to : LocalDate.now();
        List<Transaction> transactions = transactionRepository.findByDateBetweenOrderByDateAsc(effectiveFrom, effectiveTo);
        return transactions.stream()
                .filter(t -> t.getAmount().signum() < 0)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, t -> t.getAmount().abs(), BigDecimal::add)))
                .entrySet().stream()
                .map(entry -> new CategorySpending(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategorySpending::total).reversed())
                .toList();
    }

    @GetMapping("/monthly-trend")
    public List<MonthlyAmount> monthlyTrend(@RequestParam(defaultValue = "12") int months) {
        YearMonth current = YearMonth.from(LocalDate.now());
        YearMonth start = current.minusMonths(months - 1L);
        List<Transaction> transactions = transactionRepository.findByDateBetweenOrderByDateAsc(
                start.atDay(1), current.atEndOfMonth());
        Map<YearMonth, BigDecimal> byMonth = sumByMonth(transactions,
                t -> t.getAmount().signum() < 0,
                t -> t.getAmount().abs());
        List<MonthlyAmount> result = new ArrayList<>();
        for (YearMonth ym = start; !ym.isAfter(current); ym = ym.plusMonths(1)) {
            result.add(new MonthlyAmount(ym.toString(), byMonth.getOrDefault(ym, BigDecimal.ZERO)));
        }
        return result;
    }

    @GetMapping("/income-vs-expenses")
    public List<MonthlyIncomeExpense> incomeVsExpenses(@RequestParam(defaultValue = "12") int months) {
        YearMonth current = YearMonth.from(LocalDate.now());
        YearMonth start = current.minusMonths(months - 1L);
        List<Transaction> transactions = transactionRepository.findByDateBetweenOrderByDateAsc(
                start.atDay(1), current.atEndOfMonth());
        Map<YearMonth, BigDecimal> income = sumByMonth(transactions,
                t -> t.getAmount().signum() > 0,
                Transaction::getAmount);
        Map<YearMonth, BigDecimal> expenses = sumByMonth(transactions,
                t -> t.getAmount().signum() < 0,
                t -> t.getAmount().abs());
        List<MonthlyIncomeExpense> result = new ArrayList<>();
        for (YearMonth ym = start; !ym.isAfter(current); ym = ym.plusMonths(1)) {
            result.add(new MonthlyIncomeExpense(
                    ym.toString(),
                    income.getOrDefault(ym, BigDecimal.ZERO),
                    expenses.getOrDefault(ym, BigDecimal.ZERO)));
        }
        return result;
    }

    private Map<YearMonth, BigDecimal> sumByMonth(List<Transaction> transactions,
                                                  Predicate<Transaction> filter,
                                                  Function<Transaction, BigDecimal> mapper) {
        return transactions.stream()
                .filter(filter)
                .collect(Collectors.groupingBy(
                        t -> YearMonth.from(t.getDate()),
                        Collectors.reducing(BigDecimal.ZERO, mapper, BigDecimal::add)));
    }

    public record CategorySpending(String category, BigDecimal total) {
    }

    public record MonthlyAmount(String yearMonth, BigDecimal total) {
    }

    public record MonthlyIncomeExpense(String yearMonth, BigDecimal income, BigDecimal expenses) {
    }
}
