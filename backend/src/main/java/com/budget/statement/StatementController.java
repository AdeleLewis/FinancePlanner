package com.budget.statement;

import com.budget.category.Categorizer;
import com.budget.transaction.Transaction;
import com.budget.transaction.TransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final CsvParser csvParser;
    private final Categorizer categorizer;
    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;

    public StatementController(CsvParser csvParser,
                               Categorizer categorizer,
                               StatementRepository statementRepository,
                               TransactionRepository transactionRepository) {
        this.csvParser = csvParser;
        this.categorizer = categorizer;
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UploadResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        List<Transaction> transactions = csvParser.parse(file.getInputStream());
        Statement statement = statementRepository.save(new Statement(file.getOriginalFilename(), transactions.size()));
        transactions.forEach(transaction -> {
            transaction.setStatementId(statement.getId());
            categorizer.apply(transaction);
        });
        transactionRepository.saveAll(transactions);
        return ResponseEntity.ok(new UploadResult(statement.getId(), transactions.size()));
    }

    @GetMapping
    public List<Statement> list() {
        return statementRepository.findAll();
    }

    public record UploadResult(Long statementId, int rowCount) {
    }
}
