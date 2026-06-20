package com.budget.transaction;

import com.budget.category.Categorizer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/recategorize")
    @Transactional
    public RecategorizeResult recategorize() {
        List<Transaction> all = transactionRepository.findAll();
        all.forEach(categorizer::apply);
        transactionRepository.saveAll(all);
        return new RecategorizeResult(all.size());
    }

    public record RecategorizeResult(int updatedCount) {
    }
}
