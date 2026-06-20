package com.budget.statement;

import com.budget.transaction.Transaction;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CsvParser {

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy")
    };

    public List<Transaction> parse(InputStream input) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
             CSVParser csv = CSVParser.parse(reader, format)) {

            ColumnMapping mapping = detectMapping(csv.getHeaderMap().keySet());
            List<Transaction> transactions = new ArrayList<>();
            for (CSVRecord row : csv) {
                LocalDate date = parseDate(row.get(mapping.dateColumn()));
                String description = row.get(mapping.descriptionColumn());
                BigDecimal amount = parseAmount(row, mapping);
                transactions.add(new Transaction(date, description, amount));
            }
            return transactions;
        }
    }

    private ColumnMapping detectMapping(Set<String> headers) {
        String date = findHeader(headers, "date", "transaction date");
        String description = findHeader(headers, "description", "reference", "narrative", "memo", "details");
        String amount = findHeader(headers, "amount", "value");
        String credit = findHeader(headers, "credit", "money in", "paid in");
        String debit = findHeader(headers, "debit", "money out", "paid out");
        if (date == null || description == null) {
            throw new IllegalArgumentException("CSV missing required date/description column. Found headers: " + headers);
        }
        if (amount == null && (credit == null || debit == null)) {
            throw new IllegalArgumentException("CSV must have an 'amount' column, or both 'credit' and 'debit' columns. Found headers: " + headers);
        }
        return new ColumnMapping(date, description, amount, credit, debit);
    }

    private String findHeader(Set<String> headers, String... candidates) {
        for (String header : headers) {
            String lower = header.toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (lower.contains(candidate)) {
                    return header;
                }
            }
        }
        return null;
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (Exception ignored) {
                // try next format
            }
        }
        throw new IllegalArgumentException("Unparseable date: '" + value + "'");
    }

    private BigDecimal parseAmount(CSVRecord row, ColumnMapping mapping) {
        if (mapping.amountColumn() != null) {
            return new BigDecimal(clean(row.get(mapping.amountColumn())));
        }
        String creditValue = row.get(mapping.creditColumn());
        String debitValue = row.get(mapping.debitColumn());
        if (creditValue != null && !creditValue.isBlank()) {
            return new BigDecimal(clean(creditValue));
        }
        if (debitValue != null && !debitValue.isBlank()) {
            return new BigDecimal(clean(debitValue)).negate();
        }
        return BigDecimal.ZERO;
    }

    private String clean(String raw) {
        return raw.trim().replace(",", "").replace("£", "").replace("$", "");
    }

    private record ColumnMapping(String dateColumn, String descriptionColumn, String amountColumn,
                                 String creditColumn, String debitColumn) {
    }
}
