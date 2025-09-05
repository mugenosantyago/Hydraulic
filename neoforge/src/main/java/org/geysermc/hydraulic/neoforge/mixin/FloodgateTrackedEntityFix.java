package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin directly prevents the TrackedEntity NPE by intercepting
 * the ChunkMap.TrackedEntity.removePlayer method and adding null checks.
 */
@Mixin(value = ChunkMap.TrackedEntity.class, priority = 2000)
public class FloodgateTrackedEntityFix {
    private static final Logger LOGGER = LoggerFactory.getLogger("FloodgateTrackedEntityFix");
    
    /**
     * Prevents NPE when removing Bedrock players from TrackedEntity.
     */
    @Inject(
        method = "removePlayer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventBedrockPlayerRemovalNPE(ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if this TrackedEntity is properly initialized
                ChunkMap.TrackedEntity self = (ChunkMap.TrackedEntity) (Object) this;
                
                // Use reflection to check if the entity field is null
                try {
                    java.lang.reflect.Field entityField = ChunkMap.TrackedEntity.class.getDeclaredField("entity");
                    entityField.setAccessible(true);
                    Object entity = entityField.get(self);
                    
                    if (entity == null) {
                        LOGGER.info("FloodgateTrackedEntityFix: Prevented removePlayer NPE for Bedrock player {} (entity is null)", playerName);
                        ci.cancel(); // Prevent the NPE by not executing removePlayer
                        return;
                    }
                } catch (Exception reflectionException) {
                    LOGGER.debug("FloodgateTrackedEntityFix: Could not check entity field: {}", reflectionException.getMessage());
                }
                
                // Additional safety check - verify the TrackedEntity is in a valid state
                try {
                    // Check if seenBy set exists and is not null
                    java.lang.reflect.Field seenByField = ChunkMap.TrackedEntity.class.getDeclaredField("seenBy");
                    seenByField.setAccessible(true);
                    Object seenBy = seenByField.get(self);
                    
                    if (seenBy == null) {
                        LOGGER.info("FloodgateTrackedEntityFix: Prevented removePlayer NPE for Bedrock player {} (seenBy is null)", playerName);
                        ci.cancel(); // Prevent the NPE
                        return;
                    }
                } catch (Exception seenByException) {
                    LOGGER.debug("FloodgateTrackedEntityFix: Could not check seenBy field: {}", seenByException.getMessage());
                }
                
                LOGGER.debug("FloodgateTrackedEntityFix: TrackedEntity appears valid for Bedrock player: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("FloodgateTrackedEntityFix: Exception in removePlayer prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Additional safety for updatePlayer method.
     */
    @Inject(
        method = "updatePlayer",
        at = @At("HEAD"),
        cancellable = true
    )
    private void preventBedrockPlayerUpdateNPE(ServerPlayer player, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                // Check if this TrackedEntity is properly initialized
                ChunkMap.TrackedEntity self = (ChunkMap.TrackedEntity) (Object) this;
                
                // Use reflection to check if the entity field is null
                try {
                    java.lang.reflect.Field entityField = ChunkMap.TrackedEntity.class.getDeclaredField("entity");
                    entityField.setAccessible(true);
                    Object entity = entityField.get(self);
                    
                    if (entity == null) {
                        LOGGER.info("FloodgateTrackedEntityFix: Prevented updatePlayer NPE for Bedrock player {} (entity is null)", playerName);
                        ci.cancel(); // Prevent the NPE by not executing updatePlayer
                        return;
                    }
                } catch (Exception reflectionException) {
                    LOGGER.debug("FloodgateTrackedEntityFix: Could not check entity field in updatePlayer: {}", reflectionException.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("FloodgateTrackedEntityFix: Exception in updatePlayer prevention: {}", e.getMessage());
        }
    }
}
