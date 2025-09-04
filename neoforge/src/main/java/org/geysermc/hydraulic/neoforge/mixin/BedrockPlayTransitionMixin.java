package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.geysermc.hydraulic.neoforge.util.BedrockDetectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * This mixin ensures Bedrock players properly complete the transition
 * from configuration to play phase by triggering necessary server-side actions.
 */
@Mixin(value = ServerGamePacketListenerImpl.class)
public abstract class BedrockPlayTransitionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockPlayTransitionMixin");
    
    @Shadow
    public ServerPlayer player;
    
    /**
     * When a ServerGamePacketListenerImpl is created for a Bedrock player,
     * ensure the server properly completes their world join process.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void ensureBedrockPlayerTransition(MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                LOGGER.info("BedrockPlayTransitionMixin: Detected Bedrock player in play phase: {}", 
                    player.getGameProfile().getName());
                
                // Schedule a re-sync for the next tick to ensure all data is sent
                server.execute(() -> {
                    try {
                        PlayerList playerList = server.getPlayerList();
                        
                        LOGGER.info("BedrockPlayTransitionMixin: Re-syncing player data for: {}", 
                            player.getGameProfile().getName());
                        
                        // Force a complete re-sync of player data
                        playerList.sendLevelInfo(player, player.level());
                        playerList.sendPlayerPermissionLevel(player);
                        playerList.sendAllPlayerInfo(player);
                        
                        // Ensure the player's position is synced
                        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                        
                        // Send inventory
                        player.containerMenu.sendAllDataToRemote();
                        
                        LOGGER.info("BedrockPlayTransitionMixin: Successfully re-synced data for: {}", 
                            player.getGameProfile().getName());
                        
                    } catch (Exception e) {
                        LOGGER.error("BedrockPlayTransitionMixin: Failed to re-sync player data: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockPlayTransitionMixin: Exception during initialization: {}", e.getMessage());
        }
    }
}
