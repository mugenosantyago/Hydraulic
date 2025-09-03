package org.geysermc.hydraulic.fabric.mixin;

import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
// import org.geysermc.geyser.api.GeyserApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RegistrySyncManager.class)
public class RegistrySyncManagerMixin {
    @Inject(
            method = "configureClient(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Lnet/minecraft/server/MinecraftServer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void doNotConfigureBedrock(ServerConfigurationPacketListenerImpl handler, MinecraftServer server, CallbackInfo ci) {
        try {
            // Use reflection to check for Bedrock players to avoid ClassNotFoundException
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
            if (geyserApi != null && handler.getOwner() != null) {
                Boolean isBedrockPlayer = (Boolean) geyserApiClass.getMethod("isBedrockPlayer", java.util.UUID.class)
                    .invoke(geyserApi, handler.getOwner().getId());
                if (isBedrockPlayer != null && isBedrockPlayer) {
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            // Geyser not available, skip bedrock check
        }
    }
}
