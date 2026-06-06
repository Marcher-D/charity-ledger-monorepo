package com.tcl.backend.messaging;

import java.util.UUID;

public interface WalletService {

    String generateUserWalletAddress(UUID ownerId);

    String generateCampaignWalletAddress(UUID ownerId);
}
