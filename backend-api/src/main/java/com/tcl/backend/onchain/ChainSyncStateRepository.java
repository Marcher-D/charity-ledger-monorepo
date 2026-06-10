package com.tcl.backend.onchain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainSyncStateRepository extends JpaRepository<ChainSyncState, Integer> {
}
