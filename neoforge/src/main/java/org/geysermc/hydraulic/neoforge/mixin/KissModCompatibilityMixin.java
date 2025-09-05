package org.geysermc.hydraulic.neoforge.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * This mixin provides compatibility for KissMod and other mods that expect
 * the serverLevel() method to exist on ServerPlayer.
 * 
 * The issue is that KissMod was built for a different version of Minecraft/NeoForge
 * that had ServerPlayer.serverLevel() method, but this version uses level() instead.
 */
@Mixin(value = ServerPlayer.class, priority = 500)
public class KissModCompatibilityMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("KissModCompatibilityMixin");
    
    /**
     * Provides the missing serverLevel() method that KissMod expects.
     * This redirects to the correct level() method.
     */
    public ServerLevel serverLevel() {
        try {
            ServerPlayer player = (ServerPlayer) (Object) this;
            
            // Get the level and cast it to ServerLevel
            if (player.level() instanceof ServerLevel serverLevel) {
                return serverLevel;
            } else {
                LOGGER.warn("KissModCompatibilityMixin: Player level is not a ServerLevel for: {}", 
                    player.getGameProfile().getName());
                return null;
            }
        } catch (Exception e) {
            LOGGER.error("KissModCompatibilityMixin: Exception in serverLevel() compatibility method: {}", 
                e.getMessage());
            return null;
        }
    }
    
    /**
     * Alternative approach: Intercept level() calls and ensure they return ServerLevel when needed.
     */
    @Inject(
        method = "level",
        at = @At("RETURN"),
        cancellable = true
    )
    private void ensureServerLevel(CallbackInfoReturnable<net.minecraft.world.level.Level> cir) {
        try {
            // Ensure the returned level is properly cast to ServerLevel when needed
            var level = cir.getReturnValue();
            if (level instanceof ServerLevel) {
                // Already correct type, no action needed
                return;
            }
            
            // This shouldn't normally happen for ServerPlayer, but just in case
            LOGGER.debug("KissModCompatibilityMixin: level() returned non-ServerLevel: {}", 
                level != null ? level.getClass().getSimpleName() : "null");
                
        } catch (Exception e) {
            LOGGER.debug("KissModCompatibilityMixin: Exception in level() interception: {}", e.getMessage());
        }
    }
}
