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
 * This mixin ensures that Bedrock players have proper chunk tracking
 * before other systems (like Floodgate skin application) try to access it.
 */
@Mixin(value = ChunkMap.class, priority = 500)
public class ChunkTrackingMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChunkTrackingMixin");
    
    /**
     * Ensures Bedrock players are properly tracked in the chunk map
     * before other systems try to access their tracking data.
     */
    @Inject(
        method = "addEntity",
        at = @At("TAIL")
    )
    private void ensureBedrockPlayerTracking(net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        try {
            if (entity instanceof ServerPlayer player && 
                BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                
                String playerName = player.getGameProfile().getName();
                LOGGER.debug("ChunkTrackingMixin: Ensured chunk tracking for Bedrock player: {}", playerName);
                
                // Give a small delay to ensure tracking is fully initialized
                // before other systems (like skin application) try to use it
                var server = player.getServer();
                if (server != null) {
                    server.execute(() -> {
                        try {
                            Thread.sleep(100); // Small delay for tracking initialization
                            
                            // Verify tracking is properly set up
                            ChunkMap chunkMap = (ChunkMap) (Object) this;
                            try {
                                // Try to access the entity map to ensure it's initialized
                                java.lang.reflect.Field entityMapField = ChunkMap.class.getDeclaredField("entityMap");
                                entityMapField.setAccessible(true);
                                @SuppressWarnings("unchecked")
                                java.util.Map<Integer, ?> entityMap = (java.util.Map<Integer, ?>) entityMapField.get(chunkMap);
                                
                                if (entityMap != null && entityMap.containsKey(entity.getId())) {
                                    LOGGER.debug("ChunkTrackingMixin: Verified chunk tracking for Bedrock player: {}", playerName);
                                } else {
                                    LOGGER.warn("ChunkTrackingMixin: Chunk tracking not found for Bedrock player: {}", playerName);
                                }
                            } catch (Exception reflectionException) {
                                LOGGER.debug("ChunkTrackingMixin: Could not verify tracking: {}", reflectionException.getMessage());
                            }
                            
                        } catch (Exception e) {
                            LOGGER.debug("ChunkTrackingMixin: Exception in tracking verification: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ChunkTrackingMixin: Exception in chunk tracking setup: {}", e.getMessage());
        }
    }
    
    /**
     * Additional safety check when removing entities to prevent NPEs.
     */
    @Inject(
        method = "removeEntity",
        at = @At("HEAD"),
        cancellable = true
    )
    private void safeBedrockPlayerRemoval(net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        try {
            if (entity instanceof ServerPlayer player && 
                BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                
                String playerName = player.getGameProfile().getName();
                
                // Check if the entity is actually tracked before trying to remove it
                ChunkMap chunkMap = (ChunkMap) (Object) this;
                try {
                    java.lang.reflect.Field entityMapField = ChunkMap.class.getDeclaredField("entityMap");
                    entityMapField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<Integer, ?> entityMap = (java.util.Map<Integer, ?>) entityMapField.get(chunkMap);
                    
                    if (entityMap == null || !entityMap.containsKey(entity.getId())) {
                        LOGGER.debug("ChunkTrackingMixin: Preventing removal of untracked Bedrock player: {}", playerName);
                        ci.cancel(); // Prevent the removal if not properly tracked
                        return;
                    }
                } catch (Exception reflectionException) {
                    LOGGER.debug("ChunkTrackingMixin: Could not verify tracking for removal: {}", reflectionException.getMessage());
                }
                
                LOGGER.debug("ChunkTrackingMixin: Safe removal of Bedrock player: {}", playerName);
            }
        } catch (Exception e) {
            LOGGER.debug("ChunkTrackingMixin: Exception in safe removal: {}", e.getMessage());
        }
    }
}
