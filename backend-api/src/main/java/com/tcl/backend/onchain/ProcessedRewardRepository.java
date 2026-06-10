package com.tcl.backend.onchain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedRewardRepository extends JpaRepository<ProcessedReward, String> {
}
