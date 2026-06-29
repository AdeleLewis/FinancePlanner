package com.budget.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    /** External ids already imported for a given source, used to skip duplicates on re-sync. */
    @Query("select t.externalId from Transaction t where t.source = :source and t.externalId is not null")
    Set<String> findExternalIdsBySource(String source);
}
