package com.budget.connection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {

    List<BankConnection> findByProvider(BankProvider provider);
}
