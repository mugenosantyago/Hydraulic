package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures Bedrock players are created in the overworld dimension
 * to prevent Geyser translation issues with custom dimensions.
 */
@Mixin(value = PlayerList.class, priority = 2000)
public class BedrockSpawnDimensionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockSpawnDimensionMixin");

    /**
     * Forces Bedrock players to be created in the overworld dimension.
     */
    @Inject(
        method = "placeNewPlayer",
        at = @At("HEAD")
    )
    private void ensureOverworldForBedrock(net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && player.getGameProfile() != null) {
                String playerName = player.getGameProfile().getName();
                
                // Check if this is a Bedrock player
                boolean isBedrockPlayer = false;
                try {
                    // Check if this is a Floodgate player (Bedrock players via Geyser/Floodgate start with a dot)
                    if (playerName != null && playerName.startsWith(".")) {
                        isBedrockPlayer = true;
                    }
                    
                    // Also try Geyser API check
                    if (!isBedrockPlayer) {
                        try {
                            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                            Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
                            if (geyserApi != null) {
                                Boolean result = (Boolean) geyserApiClass.getMethod("isBedrockPlayer", java.util.UUID.class)
                                    .invoke(geyserApi, player.getUUID());
                                isBedrockPlayer = result != null && result;
                            }
                        } catch (Exception geyserException) {
                            // Geyser API not available or failed
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("BedrockSpawnDimensionMixin: Error detecting Bedrock player: {}", e.getMessage());
                }
                
                if (isBedrockPlayer) {
                    Level currentLevel = player.level();
                    if (currentLevel != null) {
                        String dimensionName = currentLevel.dimension().location().toString();
                        
                        // If the player is not in the overworld, force them to overworld
                        if (!dimensionName.equals("minecraft:overworld")) {
                            LOGGER.info("BedrockSpawnDimensionMixin: Bedrock player {} is in dimension {}, forcing to overworld to prevent Geyser issues", 
                                playerName, dimensionName);
                            
                            try {
                                // Get the overworld level
                                Level overworld = player.getServer().getLevel(Level.OVERWORLD);
                                if (overworld != null) {
                                    // Use reflection to change the player's level
                                    java.lang.reflect.Field levelField = net.minecraft.world.entity.Entity.class.getDeclaredField("level");
                                    levelField.setAccessible(true);
                                    levelField.set(player, overworld);
                                    
                                    LOGGER.info("BedrockSpawnDimensionMixin: Successfully moved Bedrock player {} to overworld before login packet", playerName);
                                }
                            } catch (Exception moveException) {
                                LOGGER.error("BedrockSpawnDimensionMixin: Failed to move Bedrock player {} to overworld: {}", 
                                    playerName, moveException.getMessage());
                            }
                        } else {
                            LOGGER.info("BedrockSpawnDimensionMixin: Bedrock player {} already in overworld", playerName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("BedrockSpawnDimensionMixin: Exception in dimension check: {}", e.getMessage());
        }
    }
}
