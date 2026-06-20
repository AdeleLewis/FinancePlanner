package com.budget.savings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/savings")
public class SavingsController {

    private final SavingsSnapshotRepository snapshotRepository;
    private final SavingsGoalRepository goalRepository;
    private final SavingsRecommender recommender;

    public SavingsController(SavingsSnapshotRepository snapshotRepository,
                             SavingsGoalRepository goalRepository,
                             SavingsRecommender recommender) {
        this.snapshotRepository = snapshotRepository;
        this.goalRepository = goalRepository;
        this.recommender = recommender;
    }

    @GetMapping("/snapshots")
    public List<SavingsSnapshot> listSnapshots() {
        return snapshotRepository.findAll();
    }

    @PostMapping("/snapshots")
    public SavingsSnapshot addSnapshot(@Valid @RequestBody SnapshotRequest request) {
        return snapshotRepository.save(new SavingsSnapshot(request.amount(), request.accountName()));
    }

    @DeleteMapping("/snapshots/{id}")
    public void deleteSnapshot(@PathVariable Long id) {
        snapshotRepository.deleteById(id);
    }

    @GetMapping("/goals")
    public List<SavingsGoal> listGoals() {
        return goalRepository.findAll();
    }

    @PostMapping("/goals")
    public SavingsGoal addGoal(@Valid @RequestBody GoalRequest request) {
        return goalRepository.save(new SavingsGoal(request.name(), request.targetAmount(), request.targetDate()));
    }

    @DeleteMapping("/goals/{id}")
    public void deleteGoal(@PathVariable Long id) {
        goalRepository.deleteById(id);
    }

    @PostMapping("/recommend")
    public SavingsRecommender.Recommendation recommend(@Valid @RequestBody RecommendRequest request) {
        return recommender.recommend(request.currentSavings(), request.targetAmount(), request.targetDate());
    }

    public record SnapshotRequest(
            @NotNull @PositiveOrZero BigDecimal amount,
            String accountName) {
    }

    public record GoalRequest(
            @NotBlank String name,
            @NotNull @Positive BigDecimal targetAmount,
            @NotNull @FutureOrPresent LocalDate targetDate) {
    }

    public record RecommendRequest(
            @NotNull @PositiveOrZero BigDecimal currentSavings,
            @NotNull @Positive BigDecimal targetAmount,
            @NotNull @FutureOrPresent LocalDate targetDate) {
    }
}
