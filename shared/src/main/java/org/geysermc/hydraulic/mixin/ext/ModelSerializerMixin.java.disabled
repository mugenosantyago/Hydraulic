package org.geysermc.hydraulic.mixin.ext;

import com.llamalad7.mixinextras.sugar.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import team.unnamed.creative.model.ItemTransform;

import java.util.Map;

@Mixin(targets = "team.unnamed.creative.serialize.minecraft.model.ModelSerializer", remap = false)
public class ModelSerializerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModelSerializerMixin");

    @Redirect(
        method = "deserializeFromJson(Lcom/google/gson/JsonElement;Lnet/kyori/adventure/key/Key;)Lteam/unnamed/creative/model/Model;",
        at = @At(
            value = "INVOKE",
            target = "Lteam/unnamed/creative/model/ItemTransform$Type;valueOf(Ljava/lang/String;)Lteam/unnamed/creative/model/ItemTransform$Type;"
        )
    )
    private ItemTransform.Type redirectItemTransformTypeValueOf(String name) {
        // Redirect the ItemTransform.Type.valueOf to return null instead of throwing an exception
        // This prevents an item from failing to register when a mod is using old types
        try {
            return ItemTransform.Type.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Redirect(
        method = "deserializeFromJson(Lcom/google/gson/JsonElement;Lnet/kyori/adventure/key/Key;)Lteam/unnamed/creative/model/Model;",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        )
    )
    private Object redirectDisplayMapPut(Map instance, Object k, Object v) {
        // If the type is null, we skip adding it to the map
        if (k == null) {
            return null;
        }

        return instance.put(k, v);
    }

    @Redirect(
        method = "readElementRotation",
        at = @At(
            value = "INVOKE",
            target = "Lteam/unnamed/creative/model/ElementRotation$Builder;build()Lteam/unnamed/creative/model/ElementRotation;"
        )
    )
    private team.unnamed.creative.model.ElementRotation redirectElementRotationBuild(team.unnamed.creative.model.ElementRotation.Builder builder) {
        try {
            return builder.build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("Angle must be multiple of 22.5, in range of -45 to 45")) {
                LOGGER.warn("Invalid rotation angle in model element: {}. Using default rotation to prevent crash.", e.getMessage());
                // Return null to skip the rotation element entirely
                return null;
            } else {
                throw e;
            }
        }
    }
}
