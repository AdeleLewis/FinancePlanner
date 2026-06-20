package com.budget.savings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SavingsSnapshotRepository extends JpaRepository<SavingsSnapshot, Long> {

    Optional<SavingsSnapshot> findFirstByOrderByRecordedAtDesc();
}
