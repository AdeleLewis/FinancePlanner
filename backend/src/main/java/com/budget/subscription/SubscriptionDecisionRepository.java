package com.budget.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionDecisionRepository extends JpaRepository<SubscriptionDecision, Long> {

    Optional<SubscriptionDecision> findByMerchantKey(String merchantKey);

    void deleteByMerchantKey(String merchantKey);
}
