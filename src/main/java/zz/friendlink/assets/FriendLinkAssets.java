package zz.friendlink.assets;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import zz.friendlink.FriendLinkClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FriendLinkAssets {
    private static final Path CACHE_PATH = FMLPaths.CONFIGDIR.get().resolve("friendlink").resolve("cached_client.jar");

    private static final Map<String, ResourceLocation> REGISTERED = new LinkedHashMap<>();
    private static boolean initialized;

    private FriendLinkAssets() {
    }

    public static Path getCachedJarPath() {
        return CACHE_PATH;
    }

    public static boolean isCached() {
        return Files.exists(CACHE_PATH);
    }

    public static ResourceLocation texture(String path) {
        ResourceLocation loc = REGISTERED.get(path);
        if (loc != null) {
            return loc;
        }
        return ResourceLocation.fromNamespaceAndPath("friendlink", path);
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        if (!isCached()) {
            try {
                VersionAssetsCache.ensureCached(FMLPaths.CONFIGDIR.get());
            } catch (IOException e) {
                FriendLinkClient.LOGGER.error("[FriendLink] Failed to download assets", e);
                return;
            }
        }
        initialized = true;
        Minecraft client = Minecraft.getInstance();
        try (FileSystem jar = FileSystems.newFileSystem(CACHE_PATH)) {
            registerTextures(client, jar, "textures/gui/sprites/friends/accept.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/accept_highlighted.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/background.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/button.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/button_disabled.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/button_highlighted.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/cancel.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/friends.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/illustrations_00.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/list_separator_top.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/loading.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/reject.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/reject_highlighted.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/remove.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/toast.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/toast_background.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/multiplayer/invite.png");
            registerTextures(client, jar, "textures/gui/sprites/friends/multiplayer/join_request.png");
        } catch (IOException e) {
            FriendLinkClient.LOGGER.error("[FriendLink] Failed to register cached textures", e);
        }
    }

    private static void registerTextures(Minecraft client, FileSystem jar, String texturePath) throws IOException {
        Path jarPath = jar.getPath("assets", "minecraft", texturePath);
        if (!Files.exists(jarPath)) {
            FriendLinkClient.LOGGER.warn("[FriendLink] Texture not found in cached jar: {}", texturePath);
            return;
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("friendlink", texturePath);
        try (InputStream in = Files.newInputStream(jarPath)) {
            NativeImage image = NativeImage.read(in);
            client.getTextureManager().register(location, new DynamicTexture(image));
        }
        REGISTERED.put(texturePath, location);
    }
}
