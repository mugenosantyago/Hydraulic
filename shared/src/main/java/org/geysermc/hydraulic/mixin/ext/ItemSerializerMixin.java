package org.geysermc.hydraulic.mixin.ext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "team.unnamed.creative.serialize.minecraft.item.ItemSerializer", remap = false)
public class ItemSerializerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemSerializerMixin");

    @Inject(
        method = "readSelect",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void preventUnknownSelectPropertyCrash(
        com.google.gson.JsonObject jsonObject,
        String property,
        team.unnamed.creative.item.ItemModel fallback,
        CallbackInfoReturnable<team.unnamed.creative.item.ItemModel> cir
    ) {
        // Check if this is a known problematic property type
        if (property != null && property.equals("minecraft:component")) {
            LOGGER.warn("Skipping item model with unknown select property type: {}. Using fallback model instead.", property);
            // Return the fallback model instead of throwing an exception
            cir.setReturnValue(fallback);
        }
    }

    @Inject(
        method = "readSpecial",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void preventUnknownSpecialRenderTypeCrash(
        com.google.gson.JsonObject jsonObject,
        String type,
        CallbackInfoReturnable<team.unnamed.creative.item.ItemModel> cir
    ) {
        // Check if this is a known problematic special render type
        if (type != null && type.equals("good_nights_sleep:bed")) {
            LOGGER.warn("Skipping item model with unknown special render type: {}. Using null model instead.", type);
            // Return null to skip this special model
            cir.setReturnValue(null);
        }
    }
}
