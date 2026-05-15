package zz.friendlink.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import zz.friendlink.friends.OfficialFriendsClient;
import zz.friendlink.i18n.P2PTexts;
import zz.friendlink.settings.FriendLinkOnlineSettings;

import java.net.ProxySelector;
import java.util.concurrent.CompletableFuture;

public final class FriendLinkOnlineSettingsScreen extends Screen {
    private final Screen parent;
    private Button friendsEnabledButton;
    private Button acceptInvitesButton;
    private Component status;

    public FriendLinkOnlineSettingsScreen(Screen parent) {
        super(P2PTexts.c("settings.title"));
        this.parent = parent;
        this.status = Component.empty();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 36;

        FriendLinkOnlineSettings settings = FriendLinkOnlineSettings.get();

        this.friendsEnabledButton = this.addRenderableWidget(
            Button.builder(toggleText("settings.friends_enabled", settings.isFriendsEnabled()), button -> {
                    boolean newValue = !settings.isFriendsEnabled();
                    settings.setFriendsEnabled(newValue);
                    button.setMessage(toggleText("settings.friends_enabled", newValue));
                    syncToServer();
                })
                .bounds(centerX - 100, y, 200, 20)
                .build());

        y += 27;
        this.acceptInvitesButton = this.addRenderableWidget(
            Button.builder(toggleText("settings.accept_invites", settings.isAcceptInvites()), button -> {
                    boolean newValue = !settings.isAcceptInvites();
                    settings.setAcceptInvites(newValue);
                    button.setMessage(toggleText("settings.accept_invites", newValue));
                    syncToServer();
                })
                .bounds(centerX - 100, y, 200, 20)
                .build());

        y += 30;
        this.addRenderableWidget(Button.builder(P2PTexts.c("button.back"), button ->
                this.minecraft.setScreen(this.parent))
            .bounds(centerX - 50, this.height - 28, 100, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (!this.status.getString().isBlank()) {
            guiGraphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 44, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void syncToServer() {
        FriendLinkOnlineSettings settings = FriendLinkOnlineSettings.get();
        this.status = P2PTexts.c("settings.syncing");
        Minecraft client = Minecraft.getInstance();
        CompletableFuture
            .supplyAsync(() -> {
                OfficialFriendsClient friendsClient = new OfficialFriendsClient(
                    client.getUser().getAccessToken(), ProxySelector.getDefault());
                friendsClient.updateFriendSettings(settings.isFriendsEnabled(), settings.isAcceptInvites());
                return null;
            })
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                if (throwable != null) {
                    this.status = P2PTexts.c("settings.sync_failed");
                } else {
                    this.status = P2PTexts.c("settings.synced");
                }
            }));
    }

    private Component toggleText(String key, boolean value) {
        return P2PTexts.c(key + (value ? "_on" : "_off"));
    }
}
