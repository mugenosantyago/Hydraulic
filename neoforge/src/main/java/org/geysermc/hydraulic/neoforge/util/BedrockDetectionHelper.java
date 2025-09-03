package org.geysermc.hydraulic.neoforge.util;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
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
        boolean geyserAvailable = false;
        
        try {
            // Try Geyser API first using reflection to avoid ClassNotFoundException
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
            if (geyserApi != null) {
                geyserAvailable = true;
                Boolean result = (Boolean) geyserApiClass.getMethod("isBedrockPlayer", java.util.UUID.class)
                    .invoke(geyserApi, listener.getOwner().getId());
                isBedrockFromGeyser = result != null && result;
            }
        } catch (Exception geyserException) {
            LOGGER.debug("BedrockDetectionHelper: Geyser check failed (this is normal if Geyser is not installed): {}", geyserException.getMessage());
            geyserAvailable = false;
        }
        
        // Check if this is a Floodgate player (Bedrock players via Geyser/Floodgate start with a dot)
        String playerName = listener.getOwner().getName();
        if (playerName != null && playerName.startsWith(".")) {
            isBedrockFromFloodgate = true;
        }
        
        // If Geyser is not available, only rely on Floodgate naming convention
        // This prevents false positives when Geyser is missing
        boolean isBedrock = geyserAvailable ? (isBedrockFromGeyser || isBedrockFromFloodgate) : isBedrockFromFloodgate;
        
        LOGGER.debug("BedrockDetectionHelper: Player {} - Bedrock: {} (Geyser available: {}, Geyser: {}, Floodgate: {})", 
            playerName, isBedrock, geyserAvailable, isBedrockFromGeyser, isBedrockFromFloodgate);
            
        return isBedrock;
    }
    
    /**
     * Checks if a player name indicates a Bedrock player (Floodgate naming convention).
     */
    public static boolean isFloodgatePlayer(String playerName) {
        return playerName != null && playerName.startsWith(".");
    }
}
