package zz.friendlink.assets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import zz.friendlink.FriendLinkClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

public final class VersionAssetsCache {
    private static final String VERSION = "26.2-snapshot-7";

    private VersionAssetsCache() {
    }

    public static Path getCachedJarPath(Path configDir) {
        return configDir.resolve("friendlink").resolve("cached_client.jar");
    }

    public static boolean isCached(Path configDir) {
        return Files.exists(getCachedJarPath(configDir));
    }

    public static void ensureCached(Path configDir) throws IOException {
        Path jarPath = getCachedJarPath(configDir);
        if (Files.exists(jarPath)) {
            return;
        }
        FriendLinkClient.LOGGER.info("[FriendLink] Downloading Minecraft {} client for asset extraction...", VERSION);
        Files.createDirectories(jarPath.getParent());

        String versionManifestJson = fetchString("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
        JsonObject manifest = JsonParser.parseString(versionManifestJson).getAsJsonObject();
        String versionUrl = null;
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject entry = element.getAsJsonObject();
            if (VERSION.equals(entry.get("id").getAsString())) {
                versionUrl = entry.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new IOException("Version " + VERSION + " not found in launcher manifest");
        }

        String versionDataJson = fetchString(versionUrl);
        JsonObject versionData = JsonParser.parseString(versionDataJson).getAsJsonObject();
        String clientUrl = versionData.getAsJsonObject("downloads")
            .getAsJsonObject("client")
            .get("url").getAsString();

        FriendLinkClient.LOGGER.info("[FriendLink] Downloading from {}", clientUrl);
        try (InputStream in = new URL(clientUrl).openStream();
             OutputStream out = Files.newOutputStream(jarPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
        }
        FriendLinkClient.LOGGER.info("[FriendLink] Cached client jar to {}", jarPath);
    }

    private static String fetchString(String urlStr) throws IOException {
        try (InputStream in = URI.create(urlStr).toURL().openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining());
        }
    }
}
