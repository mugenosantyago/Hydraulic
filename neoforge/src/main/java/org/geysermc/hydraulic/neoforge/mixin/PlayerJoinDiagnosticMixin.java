package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic mixin to help identify why Java players might be treated as Bedrock players.
 */
@Mixin(net.minecraft.server.players.PlayerList.class)
public class PlayerJoinDiagnosticMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerJoinDiagnosticMixin");

    @Inject(
        method = "placeNewPlayer",
        at = @At("HEAD")
    )
    private void diagnosePlayerJoin(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        // FORCE LOG TO CONSOLE - This should always appear
        System.out.println("=== HYDRAULIC DIAGNOSTIC: PLAYER JOIN ===");
        LOGGER.error("=== HYDRAULIC DIAGNOSTIC: PLAYER JOIN ===");
        try {
            if (player != null) {
                String playerName = player.getGameProfile().getName();
                java.util.UUID playerId = player.getUUID();
                
                // Check Floodgate detection
                boolean isFloodgatePlayer = BedrockDetectionHelper.isFloodgatePlayer(playerName);
                
                // Check Geyser API detection
                boolean isGeyserBedrock = false;
                boolean geyserAvailable = false;
                try {
                    Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                    Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                    if (geyserApi != null) {
                        geyserAvailable = true;
                        Boolean result = (Boolean) geyserApiClass.getMethod("isBedrockPlayer", java.util.UUID.class)
                            .invoke(geyserApi, playerId);
                        isGeyserBedrock = result != null && result;
                    }
                } catch (Exception e) {
                    // Geyser not available
                }
                
                System.out.println("Player: " + playerName + " (UUID: " + playerId + ")");
                System.out.println("Name starts with dot: " + playerName.startsWith("."));
                System.out.println("Floodgate detection: " + isFloodgatePlayer);
                System.out.println("Geyser available: " + geyserAvailable);
                System.out.println("Geyser Bedrock detection: " + isGeyserBedrock);
                System.out.println("Final Bedrock status: " + (isFloodgatePlayer || isGeyserBedrock));
                System.out.println("=== END DIAGNOSTIC ===");
                
                LOGGER.error("Player: {} (UUID: {})", playerName, playerId);
                LOGGER.error("Name starts with dot: {}", playerName.startsWith("."));
                LOGGER.error("Floodgate detection: {}", isFloodgatePlayer);
                LOGGER.error("Geyser available: {}", geyserAvailable);
                LOGGER.error("Geyser Bedrock detection: {}", isGeyserBedrock);
                LOGGER.error("Final Bedrock status: {}", isFloodgatePlayer || isGeyserBedrock);
            }
        } catch (Exception e) {
            LOGGER.error("PlayerJoinDiagnosticMixin: Error in diagnostic: {}", e.getMessage(), e);
        }
    }
}
