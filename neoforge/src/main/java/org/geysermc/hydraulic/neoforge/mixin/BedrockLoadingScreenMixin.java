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
                
                // Schedule immediate and delayed attempts to force loading screen exit
                var serverInstance = player.getServer();
                if (serverInstance != null) {
                    // Immediate attempt
                    serverInstance.execute(() -> {
                        sendLoadingScreenExitPackets(player, "immediate");
                    });
                    
                    // Delayed attempts with increasing delays
                    java.util.concurrent.ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    
                    // 250ms delay
                    executor.schedule(() -> {
                        sendLoadingScreenExitPackets(player, "250ms");
                    }, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // 500ms delay
                    executor.schedule(() -> {
                        sendLoadingScreenExitPackets(player, "500ms");
                    }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // 1000ms delay
                    executor.schedule(() -> {
                        sendLoadingScreenExitPackets(player, "1000ms");
                    }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                    
                    // 2000ms delay - final attempt
                    executor.schedule(() -> {
                        sendLoadingScreenExitPackets(player, "2000ms-final");
                        executor.shutdown();
                    }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
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
            
            // Critical packet sequence for Bedrock loading screen exit
            try {
                // 1. Send player abilities (critical for client state)
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket(player.getAbilities()));
                
                // 2. Send level chunks load start event
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
                
                // 3. Force position sync
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                
                // 4. Send game mode confirmation
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.CHANGE_GAME_MODE, 
                    player.gameMode.getGameModeForPlayer().getId()));
                
                // 5. Send player info update
                var playerList = player.getServer().getPlayerList();
                if (playerList != null) {
                    playerList.sendAllPlayerInfo(player);
                }
                
                // 6. Send inventory sync
                if (player.containerMenu != null) {
                    player.containerMenu.sendAllDataToRemote();
                }
                
                // 7. Send experience sync
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(
                    player.experienceProgress, player.totalExperience, player.experienceLevel));
                
                // 8. Send health sync
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(
                    player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
                
                // 9. Critical: Send level event that should trigger loading completion
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
                    net.minecraft.network.protocol.game.ClientboundGameEventPacket.STOP_RAINING, 0.0F));
                
                // 10. Send another position update to ensure client knows where they are
                player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                
                // 11. Force immediate packet delivery
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
