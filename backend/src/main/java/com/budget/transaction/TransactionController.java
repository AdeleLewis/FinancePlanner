package com.budget.transaction;

import com.budget.category.Categorizer;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final Categorizer categorizer;

    public TransactionController(TransactionRepository transactionRepository, Categorizer categorizer) {
        this.transactionRepository = transactionRepository;
        this.categorizer = categorizer;
    }

    @GetMapping
    public List<Transaction> list() {
        return transactionRepository.findAll();
    }

    /** The categories the UI offers in the recategorise dropdown. */
    @GetMapping("/categories")
    public List<String> categories() {
        return Categorizer.KNOWN_CATEGORIES;
    }

    /**
     * Manually set one transaction's category. This is also how the app learns: the chosen category is
     * remembered for the merchant (see {@link Categorizer#learn}) so future transactions from the same place
     * are categorised the same way, and the row is flagged so bulk re-categorisation won't overwrite it.
     */
    @PostMapping("/{id}/category")
    @Transactional
    public Transaction setCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        if (request.category() == null || request.category().isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No transaction with id " + id));
        String category = request.category().trim();
        transaction.setCategory(category);
        transaction.setUserCategorized(true);
        categorizer.learn(transaction.getDescription(), category);
        return transactionRepository.save(transaction);
    }

    /**
     * Re-run categorisation across all transactions (e.g. after the rules or learned mappings changed).
     * Transactions the user has manually categorised are left untouched — their choice wins.
     */
    @PostMapping("/recategorize")
    @Transactional
    public RecategorizeResult recategorize() {
        List<Transaction> automatic = transactionRepository.findAll().stream()
                .filter(t -> !t.isUserCategorized())
                .toList();
        automatic.forEach(categorizer::apply);
        transactionRepository.saveAll(automatic);
        return new RecategorizeResult(automatic.size());
    }

    /** Delete a single transaction. */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        transactionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Delete every transaction. Handy for clearing out test/fake data; returns how many were removed. */
    @DeleteMapping
    @Transactional
    public DeleteResult clear() {
        long removed = transactionRepository.count();
        transactionRepository.deleteAll();
        return new DeleteResult(removed);
    }

    public record CategoryRequest(String category) {
    }

    public record RecategorizeResult(int updatedCount) {
    }

    public record DeleteResult(long deletedCount) {
    }
}
