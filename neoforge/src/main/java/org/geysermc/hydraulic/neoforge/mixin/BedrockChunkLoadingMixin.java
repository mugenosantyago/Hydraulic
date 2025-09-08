package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures that Bedrock players receive the necessary chunk loading packets
 * to complete their loading screen. The key issue is that Bedrock clients need explicit
 * chunk data and loading completion signals to exit the loading screen.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1500)
public class BedrockChunkLoadingMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockChunkLoadingMixin");
    
    @Shadow
    public ServerPlayer player;
    
    // Track which players have received chunk loading packets
    private static final java.util.Set<String> chunkLoadingSent = java.util.concurrent.ConcurrentHashMap.newKeySet();
    
    /**
     * Intercepts player connection initialization to force chunk loading for Bedrock players.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void forceChunkLoadingForBedrock(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                
                if (chunkLoadingSent.contains(playerName)) {
                    LOGGER.debug("BedrockChunkLoadingMixin: Chunk loading already sent for: {}", playerName);
                    return;
                }
                
                LOGGER.info("BedrockChunkLoadingMixin: Forcing chunk loading for Bedrock player: {}", playerName);
                chunkLoadingSent.add(playerName);
                
                // Schedule chunk loading with multiple attempts
                server.execute(() -> {
                    try {
                        // Multiple attempts with increasing delays
                        for (int attempt = 1; attempt <= 4; attempt++) {
                            final int currentAttempt = attempt;
                            
                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor().schedule(() -> {
                                try {
                                    forceChunkLoading(player, server, currentAttempt);
                                } catch (Exception e) {
                                    LOGGER.error("BedrockChunkLoadingMixin: Exception in chunk loading attempt {} for {}: {}", 
                                        currentAttempt, playerName, e.getMessage());
                                }
                            }, attempt * 300L, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                    } catch (Exception e) {
                        LOGGER.error("BedrockChunkLoadingMixin: Exception scheduling chunk loading for {}: {}", 
                            playerName, e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockChunkLoadingMixin: Exception in constructor: {}", e.getMessage());
        }
    }
    
    /**
     * Forces chunk loading completion for Bedrock players.
     */
    private void forceChunkLoading(ServerPlayer player, net.minecraft.server.MinecraftServer server, int attempt) {
        try {
            String playerName = player.getGameProfile().getName();
            
            if (player.connection == null || !player.connection.getConnection().isConnected()) {
                LOGGER.debug("BedrockChunkLoadingMixin: Player {} no longer connected (attempt {})", playerName, attempt);
                return;
            }
            
            LOGGER.info("BedrockChunkLoadingMixin: Attempt {} - Forcing chunk loading for: {}", 
                attempt, playerName);
            
            // Phase 1: Send chunk cache configuration
            sendChunkCacheConfiguration(player, attempt);
            
            // Phase 2: Force load surrounding chunks
            forceLoadSurroundingChunks(player, attempt);
            
            // Phase 3: Send loading completion signals
            sendChunkLoadingCompletionSignals(player, attempt);
            
            LOGGER.info("BedrockChunkLoadingMixin: Completed chunk loading attempt {} for: {}", 
                attempt, playerName);
            
        } catch (Exception e) {
            LOGGER.error("BedrockChunkLoadingMixin: Exception in chunk loading attempt {} for {}: {}", 
                attempt, player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Sends chunk cache configuration packets.
     */
    private void sendChunkCacheConfiguration(ServerPlayer player, int attempt) {
        try {
            if (player.connection == null) return;
            
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockChunkLoadingMixin: Sending chunk cache config for {} (attempt {})", playerName, attempt);
            
            // Set chunk cache center to player position
            BlockPos playerPos = player.blockPosition();
            ChunkPos centerChunk = new ChunkPos(playerPos);
            
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(centerChunk.x, centerChunk.z));
            
            // Set chunk cache radius (reduced for Bedrock compatibility)
            int radius = Math.min(6, player.connection.getConnection().getAverageReceivedPackets() > 50 ? 8 : 4);
            player.connection.send(new ClientboundSetChunkCacheRadiusPacket(radius));
            
            // Send level loading start signal
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            // Force immediate delivery
            player.connection.getConnection().flushChannel();
            
            LOGGER.debug("BedrockChunkLoadingMixin: Sent chunk cache config for {} (center: {}, {}, radius: {})", 
                playerName, centerChunk.x, centerChunk.z, radius);
            
        } catch (Exception e) {
            LOGGER.error("BedrockChunkLoadingMixin: Failed to send chunk cache config for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Forces loading of chunks around the player.
     */
    private void forceLoadSurroundingChunks(ServerPlayer player, int attempt) {
        try {
            if (player.connection == null) return;
            
            String playerName = player.getGameProfile().getName();
            ServerLevel level = (ServerLevel) player.level();
            
            LOGGER.debug("BedrockChunkLoadingMixin: Force loading chunks for {} (attempt {})", playerName, attempt);
            
            BlockPos playerPos = player.blockPosition();
            ChunkPos centerChunk = new ChunkPos(playerPos);
            
            // Load chunks in a smaller radius for Bedrock compatibility
            int loadRadius = 2; // 5x5 chunk area around player
            int chunksLoaded = 0;
            
            for (int x = -loadRadius; x <= loadRadius; x++) {
                for (int z = -loadRadius; z <= loadRadius; z++) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);
                    
                    try {
                        // Get or load the chunk
                        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
                        
                        if (chunk != null) {
                            // Send chunk data to client
                            try {
                                ClientboundLevelChunkWithLightPacket chunkPacket = 
                                    new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
                                player.connection.send(chunkPacket);
                                chunksLoaded++;
                                
                                // Add small delay between chunks to prevent overwhelming
                                if (chunksLoaded % 3 == 0) {
                                    Thread.sleep(10);
                                }
                            } catch (Exception chunkPacketException) {
                                LOGGER.debug("BedrockChunkLoadingMixin: Could not send chunk packet for {} at {},{}: {}", 
                                    playerName, chunkPos.x, chunkPos.z, chunkPacketException.getMessage());
                            }
                        }
                    } catch (Exception chunkException) {
                        LOGGER.debug("BedrockChunkLoadingMixin: Could not load chunk for {} at {},{}: {}", 
                            playerName, chunkPos.x, chunkPos.z, chunkException.getMessage());
                    }
                }
            }
            
            // Force delivery of all chunk packets
            player.connection.getConnection().flushChannel();
            
            LOGGER.info("BedrockChunkLoadingMixin: Loaded {} chunks for {} (attempt {})", 
                chunksLoaded, playerName, attempt);
            
        } catch (Exception e) {
            LOGGER.error("BedrockChunkLoadingMixin: Failed to force load chunks for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Sends chunk loading completion signals.
     */
    private void sendChunkLoadingCompletionSignals(ServerPlayer player, int attempt) {
        try {
            if (player.connection == null) return;
            
            String playerName = player.getGameProfile().getName();
            LOGGER.debug("BedrockChunkLoadingMixin: Sending chunk loading completion for {} (attempt {})", playerName, attempt);
            
            // Send world border initialization (helps with loading completion)
            try {
                net.minecraft.world.level.border.WorldBorder worldBorder = player.level().getWorldBorder();
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket(worldBorder));
            } catch (Exception borderException) {
                LOGGER.debug("BedrockChunkLoadingMixin: Could not send world border: {}", borderException.getMessage());
            }
            
            // Send level loading completion signal
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
            
            // Send another completion signal with a different event
            player.connection.send(new ClientboundGameEventPacket(
                ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            
            // Send time update (often triggers final loading completion)
            long worldTime = player.level().getGameTime();
            long dayTime = player.level().getDayTime();
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(worldTime, dayTime, true));
            
            // Force final delivery
            player.connection.getConnection().flushChannel();
            
            LOGGER.debug("BedrockChunkLoadingMixin: Sent chunk loading completion signals for {} (attempt {})", 
                playerName, attempt);
            
        } catch (Exception e) {
            LOGGER.error("BedrockChunkLoadingMixin: Failed to send completion signals for {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
        }
    }
    
    /**
     * Clean up when players disconnect.
     */
    @Inject(
        method = "onDisconnect",
        at = @At("HEAD")
    )
    private void cleanupChunkLoading(net.minecraft.network.DisconnectionDetails details, CallbackInfo ci) {
        try {
            if (this.player != null) {
                String playerName = this.player.getGameProfile().getName();
                if (chunkLoadingSent.remove(playerName)) {
                    LOGGER.debug("BedrockChunkLoadingMixin: Cleaned up chunk loading tracking for: {}", playerName);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockChunkLoadingMixin: Exception during cleanup: {}", e.getMessage());
        }
    }
}
