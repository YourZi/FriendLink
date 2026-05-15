package zz.friendlink.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import zz.friendlink.FriendLinkClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FriendLinkOnlineSettings {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("friendlink").resolve("online_settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FriendLinkOnlineSettings instance;

    private boolean friendsEnabled = true;
    private boolean acceptInvites = true;

    private FriendLinkOnlineSettings() {
    }

    public static FriendLinkOnlineSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public boolean isFriendsEnabled() {
        return friendsEnabled;
    }

    public void setFriendsEnabled(boolean friendsEnabled) {
        this.friendsEnabled = friendsEnabled;
        save();
    }

    public boolean isAcceptInvites() {
        return acceptInvites;
    }

    public void setAcceptInvites(boolean acceptInvites) {
        this.acceptInvites = acceptInvites;
        save();
    }

    private static FriendLinkOnlineSettings load() {
        FriendLinkOnlineSettings settings = new FriendLinkOnlineSettings();
        try {
            if (Files.exists(CONFIG_PATH)) {
                String content = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("friendsEnabled")) {
                    settings.friendsEnabled = json.get("friendsEnabled").getAsBoolean();
                }
                if (json.has("acceptInvites")) {
                    settings.acceptInvites = json.get("acceptInvites").getAsBoolean();
                }
            }
        } catch (IOException e) {
            FriendLinkClient.LOGGER.warn("[FriendLink] Failed to load online settings", e);
        }
        return settings;
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("friendsEnabled", friendsEnabled);
            json.addProperty("acceptInvites", acceptInvites);
            Files.writeString(CONFIG_PATH, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FriendLinkClient.LOGGER.warn("[FriendLink] Failed to save online settings", e);
        }
    }
}
