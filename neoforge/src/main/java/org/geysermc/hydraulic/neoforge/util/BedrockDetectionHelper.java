package org.geysermc.hydraulic.neoforge.util;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for detecting Bedrock players across different mixins.
 */
public class BedrockDetectionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockDetectionHelper");
    
    /**
     * Checks if a player is a Bedrock player using multiple detection methods.
     */
    public static boolean isBedrockPlayer(ServerConfigurationPacketListenerImpl listener) {
        if (listener == null || listener.getOwner() == null) {
            return false;
        }
        
        boolean isBedrockFromGeyser = false;
        boolean isBedrockFromFloodgate = false;
        
        try {
            // Try Geyser API first
            isBedrockFromGeyser = GeyserApi.api() != null && GeyserApi.api().isBedrockPlayer(listener.getOwner().getId());
        } catch (Exception geyserException) {
            LOGGER.debug("BedrockDetectionHelper: Geyser check failed: {}", geyserException.getMessage());
        }
        
        // Check if this is a Floodgate player (Bedrock players via Geyser/Floodgate start with a dot)
        String playerName = listener.getOwner().getName();
        if (playerName != null && playerName.startsWith(".")) {
            isBedrockFromFloodgate = true;
        }
        
        boolean isBedrock = isBedrockFromGeyser || isBedrockFromFloodgate;
        
        LOGGER.debug("BedrockDetectionHelper: Player {} - Bedrock: {} (Geyser: {}, Floodgate: {})", 
            playerName, isBedrock, isBedrockFromGeyser, isBedrockFromFloodgate);
            
        return isBedrock;
    }
    
    /**
     * Checks if a player name indicates a Bedrock player (Floodgate naming convention).
     */
    public static boolean isFloodgatePlayer(String playerName) {
        return playerName != null && playerName.startsWith(".");
    }
}
