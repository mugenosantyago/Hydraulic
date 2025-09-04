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
 * This mixin ensures Bedrock players receive all necessary packets
 * to transition from configuration to play phase properly.
 */
@Mixin(value = ServerGamePacketListenerImpl.class)
public abstract class BedrockPlayTransitionMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("BedrockPlayTransitionMixin");
    
    @Shadow
    public ServerPlayer player;
    
    @Shadow
    public abstract void send(net.minecraft.network.protocol.Packet<?> packet);
    
    /**
     * When a ServerGamePacketListenerImpl is created for a Bedrock player,
     * ensure they receive all necessary packets to complete the transition.
     */
    @Inject(
        method = "<init>",
        at = @At("TAIL")
    )
    private void ensureBedrockPlayerTransition(MinecraftServer server, net.minecraft.network.Connection connection, ServerPlayer player, net.minecraft.server.network.CommonListenerCookie cookie, CallbackInfo ci) {
        try {
            if (player != null && BedrockDetectionHelper.isFloodgatePlayer(player.getGameProfile().getName())) {
                LOGGER.info("BedrockPlayTransitionMixin: Ensuring play phase transition for Bedrock player: {}", 
                    player.getGameProfile().getName());
                
                // Schedule packet sending for next tick to ensure everything is initialized
                server.execute(() -> {
                    try {
                        sendTransitionPackets(player);
                    } catch (Exception e) {
                        LOGGER.error("BedrockPlayTransitionMixin: Failed to send transition packets: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.debug("BedrockPlayTransitionMixin: Exception during initialization: {}", e.getMessage());
        }
    }
    
    /**
     * Send all necessary packets to complete the play phase transition for Bedrock players.
     */
    private void sendTransitionPackets(ServerPlayer player) {
        try {
            LOGGER.info("BedrockPlayTransitionMixin: Sending transition packets to Bedrock player: {}", 
                player.getGameProfile().getName());
            
            MinecraftServer server = player.getServer();
            if (server == null) return;
            
            // Send game join packet
            ClientboundLoginPacket loginPacket = new ClientboundLoginPacket(
                player.getId(),
                server.levelKeys(),
                server.getMaxPlayers(),
                8, // viewDistance
                8, // simulationDistance
                false, // reducedDebugInfo
                !server.usesAuthentication(),
                false, // doLimitedCrafting
                player.gameMode.getGameModeForPlayer(),
                player.gameMode.getPreviousGameModeForPlayer(),
                false, // isDebug
                false, // isFlatWorld
                player.getLastDeathLocation(),
                player.getPortalCooldown(),
                server.enforceSecureProfile()
            );
            send(loginPacket);
            
            // Send player abilities
            send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            
            // Send difficulty
            send(new ClientboundChangeDifficultyPacket(
                server.getWorldData().getDifficulty(),
                server.getWorldData().isDifficultyLocked()
            ));
            
            // Send held item
            send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelected()));
            
            // Send spawn position
            BlockPos spawn = player.level().getSharedSpawnPos();
            send(new ClientboundSetDefaultSpawnPositionPacket(spawn, 0.0F));
            
            // Update player info
            send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(java.util.Collections.singleton(player)));
            
            // Update recipes
            send(new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes(), player.registryAccess()));
            
            // Send commands
            server.getCommands().sendCommands(player);
            
            // Send player list updates
            PlayerList playerList = server.getPlayerList();
            playerList.sendPlayerPermissionLevel(player);
            
            // Send entity status
            send(new ClientboundEntityEventPacket(player, (byte) (player.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23)));
            
            // Send inventory
            player.getInventory().tick();
            player.containerMenu.sendAllDataToRemote();
            
            // Send health and experience
            send(new ClientboundSetHealthPacket(player.getHealth(), player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
            send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            
            // Force position sync
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            
            // Send chunk data
            send(new ClientboundSetChunkCacheRadiusPacket(8));
            send(new ClientboundSetSimulationDistancePacket(8));
            send(new ClientboundSetChunkCacheCenterPacket(player.chunkPosition().x, player.chunkPosition().z));
            
            // Update weather if needed
            if (player.level().isRaining()) {
                send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
                send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, player.level().getRainLevel(1.0F)));
                send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, player.level().getThunderLevel(1.0F)));
            }
            
            LOGGER.info("BedrockPlayTransitionMixin: Successfully sent all transition packets to: {}", 
                player.getGameProfile().getName());
            
        } catch (Exception e) {
            LOGGER.error("BedrockPlayTransitionMixin: Failed to send transition packets to {}: {}", 
                player.getGameProfile().getName(), e.getMessage());
            e.printStackTrace();
        }
    }
}
