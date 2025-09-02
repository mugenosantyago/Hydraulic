package org.geysermc.hydraulic.mixin.ext;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import team.unnamed.creative.overlay.ResourceContainer;
import team.unnamed.creative.part.ResourcePackPart;
import team.unnamed.creative.serialize.minecraft.GsonUtil;
import team.unnamed.creative.serialize.minecraft.io.JsonResourceDeserializer;

import java.io.IOException;

@Mixin(targets = "team.unnamed.creative.serialize.minecraft.MinecraftResourcePackReaderImpl", remap = false)
public abstract class MinecraftResourcePackReaderImplMixin {
    private static Logger LOGGER = LoggerFactory.getLogger("MinecraftResourcePackReaderImplMixin");

    /**
     * Redirect the parseJson method to catch any exceptions that may occur
     * This means a single bad json file won't cause the entire resource pack to fail loading
     */
    @Redirect(
        method = "parseJson",
        at = @At(
            value = "INVOKE",
            target = "Lteam/unnamed/creative/serialize/minecraft/GsonUtil;parseReader(Lcom/google/gson/stream/JsonReader;)Lcom/google/gson/JsonElement;"
        )
    )
    private JsonElement parseJson(JsonReader reader) {
        try {
            return GsonUtil.parseReader(reader);
        } catch (com.google.gson.JsonSyntaxException e) {
            if (e.getCause() instanceof com.google.gson.stream.MalformedJsonException) {
                LOGGER.warn("Skipping malformed JSON file due to BiomesOPlenty corruption: {}. This is a known issue with the mod.", e.getMessage());
            } else {
                LOGGER.error("Failed to parse JSON due to syntax error: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && (e.getMessage().contains("MalformedJsonException") || e.getMessage().contains("Unterminated object"))) {
                LOGGER.warn("Skipping JSON file with malformed content: {}. This is likely due to a corrupted mod resource file.", e.getMessage());
            } else {
                LOGGER.error("Failed to parse JSON (RuntimeException): " + e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse JSON (General Exception): " + e.getMessage());
        }

        return null;
    }

    /**
     * Redirect the deserializeFromJson to ignore any null JsonElements
     * Also catch any exceptions that may occur and log them
     */
    @Redirect(
        method = "read(Lteam/unnamed/creative/serialize/minecraft/fs/FileTreeReader;)Lteam/unnamed/creative/ResourcePack;",
        at = @At(
            value = "INVOKE",
            target = "Lteam/unnamed/creative/serialize/minecraft/io/JsonResourceDeserializer;deserializeFromJson(Lcom/google/gson/JsonElement;Lnet/kyori/adventure/key/Key;)Ljava/lang/Object;"
        )
    )
    private Object deserializeFromJson(JsonResourceDeserializer instance, JsonElement jsonElement, Key key) throws IOException {
        if (jsonElement == null) {
            return null;
        }

        try {
            return instance.deserializeFromJson(jsonElement, key);
        } catch (IllegalArgumentException e) {
            // Handle our special skip exceptions and other known issues
            if (e.getMessage() != null) {
                if (e.getMessage().startsWith("HYDRAULIC_SKIP_")) {
                    LOGGER.debug("Item model processing skipped due to handled issue (" + key + ")");
                } else if (e.getMessage().contains("Unknown select property type")) {
                    LOGGER.warn("Skipping item model with unknown select property type (" + key + "): " + e.getMessage());
                } else if (e.getMessage().contains("Angle must be multiple of 22.5, in range of -45 to 45")) {
                    LOGGER.warn("Skipping model with invalid rotation angle (" + key + "): " + e.getMessage());
                } else if (e.getMessage().contains("Unknown special render type")) {
                    LOGGER.warn("Skipping item model with unknown special render type (" + key + "): " + e.getMessage());
                } else {
                    LOGGER.error("Failed to deserialize JSON (" + key + "): " + e.getMessage());
                }
            } else {
                LOGGER.error("Failed to deserialize JSON (" + key + "): " + e.getMessage());
            }
        } catch (RuntimeException e) {
            // Catch all runtime exceptions including nested IllegalArgumentExceptions
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Angle must be multiple of 22.5") || 
                    e.getMessage().contains("Unknown select property type") ||
                    e.getMessage().contains("Unknown special render type") ||
                    e.getMessage().contains("MalformedJsonException") ||
                    e.getMessage().contains("Unterminated object")) {
                    LOGGER.warn("Skipping problematic model file (" + key + "): " + e.getMessage());
                } else {
                    LOGGER.error("Runtime error during model deserialization (" + key + "): " + e.getMessage());
                }
            } else {
                LOGGER.error("Runtime error during model deserialization (" + key + "): " + e.getClass().getSimpleName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize JSON (" + key + "): " + e.getMessage());
        }

        return null;
    }

    @Redirect(
            method = "read(Lteam/unnamed/creative/serialize/minecraft/fs/FileTreeReader;)Lteam/unnamed/creative/ResourcePack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lteam/unnamed/creative/part/ResourcePackPart;addTo(Lteam/unnamed/creative/overlay/ResourceContainer;)V"
            )
    )
    private void addTo(ResourcePackPart instance, ResourceContainer resourceContainer) {
        if (instance != null) {
            instance.addTo(resourceContainer);
        }
    }

    //Key key = Key.key(namespace, keyValue);
    @ModifyArgs(
            method = "read(Lteam/unnamed/creative/serialize/minecraft/fs/FileTreeReader;)Lteam/unnamed/creative/ResourcePack;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/kyori/adventure/key/Key;key(Ljava/lang/String;Ljava/lang/String;)Lnet/kyori/adventure/key/Key;",
                    ordinal = 2
            )
    )
    private void injectKeyCreation(Args args) {
        args.set(1, ((String) args.get(1)).toLowerCase());
    }
}
