package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin specifically targets the loading screen issue by ensuring
 * Bedrock clients receive the exact packet sequence needed to exit loading screen.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1500)
public class BedrockLoadingScreenMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockLoadingScreenMixin");
    
    @Shadow
    public ServerPlayer player;
    
    /**
     * Hook into the game packet listener creation to force the loading screen exit sequence.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void forceLoadingScreenExit(net.minecraft.server.MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                String playerName = player.getGameProfile().getName();
                LOGGER.info("BedrockLoadingScreenMixin: Forcing loading screen exit sequence for: {}", playerName);
                
                // Schedule more conservative attempts that won't interfere with skin loading
                var serverInstance = player.getServer();
                if (serverInstance != null) {
                    java.util.concurrent.ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    
                    // Wait longer to allow skin loading to complete first
                    // 1500ms delay - after skin should be loaded
                    executor.schedule(() -> {
                        sendLoadingScreenExitPackets(player, "1500ms");
                    }, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // 3000ms delay - final attempt if still stuck
                    executor.schedule(() -> {
                        sendLoadingScreenExitPackets(player, "3000ms-final");
                        executor.shutdown();
                    }, 3000, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenMixin: Exception in loading screen exit setup: {}", e.getMessage());
        }
    }
    
    /**
     * Sends the specific packet sequence needed to force Bedrock clients out of loading screen.
     */
    private static void sendLoadingScreenExitPackets(ServerPlayer player, String attempt) {
        try {
            if (player == null || player.connection == null || !player.connection.getConnection().isConnected()) {
                return;
            }
            
            String playerName = player.getGameProfile().getName();
            LOGGER.info("BedrockLoadingScreenMixin: Sending loading screen exit packets for {} ({})", playerName, attempt);
            
            // More conservative packet sequence that won't interfere with skin loading
            try {
                // Only send the most critical packets for loading screen exit
                
                // 1. Send level chunks load start event (critical for loading screen)
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
                
                // 2. Send position sync (let client know where they are)
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                
                // 3. Send game mode (critical for client state)
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                    player.gameMode.getGameModeForPlayer().getId()));
                
                // 4. Force packet delivery
                player.connection.getConnection().flushChannel();
                
                LOGGER.info("BedrockLoadingScreenMixin: Completed loading screen exit sequence for {} ({})", playerName, attempt);
                
            } catch (Exception packetException) {
                LOGGER.error("BedrockLoadingScreenMixin: Failed to send loading screen exit packets for {} ({}): {}", 
                    playerName, attempt, packetException.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.debug("BedrockLoadingScreenMixin: Exception in loading screen exit packets: {}", e.getMessage());
        }
    }
}
