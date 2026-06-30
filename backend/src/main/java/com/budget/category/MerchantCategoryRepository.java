package com.budget.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantCategoryRepository extends JpaRepository<MerchantCategory, Long> {

    Optional<MerchantCategory> findByMerchantKey(String merchantKey);
}
