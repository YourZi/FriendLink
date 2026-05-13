package zz.officialp2p.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import zz.officialp2p.OfficialP2PBackportClient;
import zz.officialp2p.friends.OfficialFriendsClient;
import zz.officialp2p.friends.OfficialFriendsException;
import zz.officialp2p.friends.model.FriendActionRequest;
import zz.officialp2p.friends.model.FriendData;
import zz.officialp2p.friends.model.FriendDto;
import zz.officialp2p.util.Uuids;

import java.net.ProxySelector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class P2PConnectScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 330;
    private static final int ROW_COUNT = 5;
    private static final int ROW_HEIGHT = 26;
    private static final long SUCCESS_REFRESH_COOLDOWN_MS = 20_000L;
    private static final long FAILURE_REFRESH_COOLDOWN_MS = 120_000L;
    private static FriendData cachedFriendData = FriendData.empty();
    private static Component cachedStatus = Component.literal("Ready. " + OfficialP2PBackportClient.BUILD_MARKER);
    private static long nextFriendsFetchAt;

    private final Screen parent;
    private final List<Button> rowButtons = new ArrayList<>();
    private EditBox profileBox;
    private Button friendsTab;
    private Button requestsTab;
    private Button addButton;
    private Button refreshButton;
    private Button listenButton;
    private Button connectButton;
    private Button copyIdButton;
    private FriendData friendData = FriendData.empty();
    private Tab activeTab = Tab.FRIENDS;
    private UUID selectedPeer;
    private String selectedName = "";
    private Component status = Component.literal("Ready. " + OfficialP2PBackportClient.BUILD_MARKER);
    private boolean loadingFriends;

    public P2PConnectScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();

        this.friendsTab = this.addRenderableWidget(Button.builder(Component.literal("Friends"), button -> setTab(Tab.FRIENDS))
            .bounds(left + 16, top - 38, (PANEL_WIDTH - 32) / 2, 36)
            .build());
        this.requestsTab = this.addRenderableWidget(Button.builder(requestsTitle(), button -> setTab(Tab.REQUESTS))
            .bounds(left + 16 + (PANEL_WIDTH - 32) / 2, top - 38, (PANEL_WIDTH - 32) / 2, 36)
            .build());

        this.profileBox = new EditBox(this.font, left + 30, top + 20, PANEL_WIDTH - 88, 20, Component.literal("Enter Profile Name"));
        this.profileBox.setMaxLength(64);
        this.profileBox.setHint(Component.literal("Enter Profile Name"));
        this.addRenderableWidget(this.profileBox);

        this.addButton = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> addFriend())
            .bounds(left + PANEL_WIDTH - 52, top + 20, 24, 20)
            .build());

        for (int index = 0; index < ROW_COUNT; index++) {
            int row = index;
            Button button = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> selectVisibleRow(row))
                .bounds(left + 28, rowsTop() + row * ROW_HEIGHT, PANEL_WIDTH - 56, 22)
                .build());
            this.rowButtons.add(button);
        }

        int bottom = top + PANEL_HEIGHT - 34;
        this.listenButton = this.addRenderableWidget(Button.builder(Component.literal("Listen"), button -> listen())
            .bounds(left + 28, bottom, 84, 20)
            .build());
        this.connectButton = this.addRenderableWidget(Button.builder(Component.literal("Connect"), button -> connect())
            .bounds(left + 118, bottom, 92, 20)
            .build());
        this.refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refreshFriends(true))
            .bounds(left + 216, bottom, 78, 20)
            .build());
        this.copyIdButton = this.addRenderableWidget(Button.builder(Component.literal("My ID"), button -> fillMyId())
            .bounds(left + 300, bottom, 62, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.minecraft.setScreen(this.parent))
            .bounds(left + PANEL_WIDTH - 62, top + PANEL_HEIGHT + 8, 54, 20)
            .build());

        this.friendData = cachedFriendData;
        this.status = cachedStatus;
        updateWidgets();
        refreshFriends(false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = panelLeft();
        int top = panelTop();
        this.extractMenuBackground(graphics);

        drawTabBackdrops(graphics, left, top);
        drawPanel(graphics, left, top);
        drawHeader(graphics, left, top);
        drawContent(graphics, left, top);
        drawFooter(graphics, left, top);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void setTab(Tab tab) {
        this.activeTab = tab;
        this.selectedPeer = null;
        this.selectedName = "";
        updateWidgets();
    }

    private void refreshFriends(boolean manual) {
        Minecraft client = Minecraft.getInstance();
        User user = client.getUser();
        long now = System.currentTimeMillis();
        if (now < nextFriendsFetchAt) {
            long seconds = Math.max(1L, (nextFriendsFetchAt - now + 999L) / 1000L);
            this.status = Component.literal((manual ? "Refresh" : "Friends auto refresh") + " cooling down: " + seconds + "s");
            cachedStatus = this.status;
            updateWidgets();
            return;
        }

        this.loadingFriends = true;
        this.status = Component.literal("Fetching official friends...");
        cachedStatus = this.status;
        updateWidgets();

        CompletableFuture
            .supplyAsync(() -> new OfficialFriendsClient(user.getAccessToken(), ProxySelector.getDefault()).getFriendData())
            .whenComplete((data, throwable) -> client.execute(() -> {
                this.loadingFriends = false;
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = Component.literal("Friends failed: " + userMessage(cause));
                    cachedStatus = this.status;
                    nextFriendsFetchAt = System.currentTimeMillis() + FAILURE_REFRESH_COOLDOWN_MS;
                    OfficialP2PBackportClient.LOGGER.warn("Official friends UI refresh failed", cause);
                    updateWidgets();
                    return;
                }
                this.friendData = data == null ? FriendData.empty() : data;
                cachedFriendData = this.friendData;
                this.status = Component.literal("Friends loaded.");
                cachedStatus = this.status;
                nextFriendsFetchAt = System.currentTimeMillis() + SUCCESS_REFRESH_COOLDOWN_MS;
                updateWidgets();
            }));
    }

    private void addFriend() {
        Minecraft client = Minecraft.getInstance();
        String raw = this.profileBox.getValue().trim();
        if (raw.isBlank()) {
            this.status = Component.literal("Enter a profile name first.");
            return;
        }
        try {
            this.selectedPeer = Uuids.parseFlexible(raw);
            this.selectedName = "";
            this.status = Component.literal("UUID selected. Click Connect to join.");
            updateWidgets();
            return;
        } catch (IllegalArgumentException ignored) {
        }

        this.addButton.active = false;
        this.status = Component.literal("Sending friend request...");
        CompletableFuture
            .supplyAsync(() -> {
                OfficialFriendsClient friends = new OfficialFriendsClient(client.getUser().getAccessToken(), ProxySelector.getDefault());
                return friends.putFriendAction(friendAction(raw));
            })
            .whenComplete((data, throwable) -> client.execute(() -> {
                this.addButton.active = true;
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = Component.literal("Friend action failed: " + userMessage(cause));
                    OfficialP2PBackportClient.LOGGER.warn("Official friend action failed", cause);
                    return;
                }
                this.friendData = data == null ? FriendData.empty() : data;
                this.status = Component.literal("Friend request updated.");
                updateWidgets();
            }));
    }

    private FriendActionRequest friendAction(String raw) {
        return FriendActionRequest.addByName(raw);
    }

    private void listen() {
        Minecraft client = Minecraft.getInstance();
        this.listenButton.active = false;
        this.listenButton.setMessage(Component.literal("Listening..."));
        P2PUiActions.listen(client, this::setStatus)
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                this.listenButton.active = true;
                this.listenButton.setMessage(Component.literal("Listen"));
            }));
    }

    private void connect() {
        Minecraft client = Minecraft.getInstance();
        UUID peerPmid = this.selectedPeer;
        String rawPeer = this.profileBox.getValue().trim();
        if (peerPmid == null && !rawPeer.isBlank()) {
            try {
                peerPmid = Uuids.parseFlexible(rawPeer);
            } catch (IllegalArgumentException exception) {
                P2PUiActions.status(client, this::setStatus, "Select a friend or enter a valid host UUID.");
                return;
            }
        }
        if (peerPmid == null) {
            P2PUiActions.status(client, this::setStatus, "Select a friend or enter the host UUID.");
            return;
        }

        this.connectButton.active = false;
        this.connectButton.setMessage(Component.literal("Connecting..."));
        P2PUiActions.connect(client, peerPmid, this::setStatus)
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                this.connectButton.active = true;
                this.connectButton.setMessage(Component.literal("Connect"));
            }));
    }

    private void fillMyId() {
        this.profileBox.setValue(this.minecraft.getUser().getProfileId().toString());
        this.selectedPeer = null;
        this.selectedName = "";
        this.status = Component.literal("Filled your profile UUID.");
        updateWidgets();
    }

    private void selectVisibleRow(int row) {
        List<FriendDto> rows = visibleRows();
        if (row < 0 || row >= rows.size()) {
            return;
        }
        FriendDto friend = rows.get(row);
        this.selectedPeer = friend.profileId();
        this.selectedName = friend.name() == null ? "" : friend.name();
        this.profileBox.setValue(friend.profileId().toString());
        this.status = Component.literal("Selected " + displayName(friend) + ".");
        updateWidgets();
    }

    private void updateWidgets() {
        if (this.friendsTab == null) {
            return;
        }
        this.friendsTab.active = this.activeTab != Tab.FRIENDS;
        this.requestsTab.active = this.activeTab != Tab.REQUESTS;
        this.requestsTab.setMessage(requestsTitle());
        this.refreshButton.active = !this.loadingFriends && System.currentTimeMillis() >= nextFriendsFetchAt;
        this.addButton.active = !this.loadingFriends;

        List<FriendDto> rows = visibleRows();
        for (int index = 0; index < this.rowButtons.size(); index++) {
            Button button = this.rowButtons.get(index);
            boolean visible = index < rows.size();
            button.visible = visible;
            button.active = visible;
            if (visible) {
                FriendDto friend = rows.get(index);
                String prefix = friend.profileId().equals(this.selectedPeer) ? "> " : "";
                button.setMessage(Component.literal(prefix + displayName(friend) + "  " + shortId(friend.profileId())));
            }
        }
    }

    private void setStatus(String message) {
        this.status = Component.literal(message);
    }

    private void drawTabBackdrops(GuiGraphicsExtractor graphics, int left, int top) {
        int tabWidth = (PANEL_WIDTH - 32) / 2;
        int activeLeft = this.activeTab == Tab.FRIENDS ? left + 16 : left + 16 + tabWidth;
        graphics.fill(left + 16, top - 38, left + 16 + tabWidth * 2, top - 2, 0xFF1B1B1B);
        graphics.fill(activeLeft, top - 38, activeLeft + tabWidth, top - 2, 0xFF8E8E8E);
        graphics.outline(left + 16, top - 38, tabWidth, 36, 0xFF000000);
        graphics.outline(left + 16 + tabWidth, top - 38, tabWidth, 36, 0xFF000000);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left - 4, top - 4, left + PANEL_WIDTH + 4, top + PANEL_HEIGHT + 4, 0xFFDDDDDD);
        graphics.fill(left - 2, top - 2, left + PANEL_WIDTH + 2, top + PANEL_HEIGHT + 2, 0xFF000000);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF343434);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFFFFFFFF);
        graphics.outline(left + 4, top + 4, PANEL_WIDTH - 8, PANEL_HEIGHT - 8, 0xFF1C1C1C);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left + 20, top + 14, left + PANEL_WIDTH - 20, top + 64, 0xFF2A2A2A);
        graphics.text(this.font, "My profile name: " + this.minecraft.getUser().getName(), left + 30, top + 46, 0xFFCFCFCF);
        int statusColor = this.status.getString().contains("failed") ? 0xFFFFFF55 : 0xFFCFCFCF;
        graphics.text(this.font, fit(this.status.getString(), PANEL_WIDTH - 60), left + 30, top + 70, statusColor);
        graphics.fill(left + 8, top + 96, left + PANEL_WIDTH - 8, top + 97, 0xFF1C1C1C);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int left, int top) {
        List<FriendDto> rows = visibleRows();
        if (this.loadingFriends) {
            graphics.centeredText(this.font, Component.literal("Loading friends..."), left + PANEL_WIDTH / 2, top + 150, 0xFFFFFFFF);
            return;
        }
        if (!rows.isEmpty()) {
            drawRows(graphics, left);
            return;
        }

        drawEmptyScene(graphics, left + PANEL_WIDTH / 2 - 76, top + 116);
        if (this.activeTab == Tab.FRIENDS) {
            graphics.centeredText(this.font, Component.literal("Friends you add will be listed"), left + PANEL_WIDTH / 2, top + 218, 0xFFCFCFCF);
            graphics.centeredText(this.font, Component.literal("here."), left + PANEL_WIDTH / 2, top + 232, 0xFFCFCFCF);
        } else {
            graphics.centeredText(this.font, Component.literal("Friend requests will be listed"), left + PANEL_WIDTH / 2, top + 218, 0xFFCFCFCF);
            graphics.centeredText(this.font, Component.literal("here."), left + PANEL_WIDTH / 2, top + 232, 0xFFCFCFCF);
        }
        graphics.centeredText(this.font, Component.literal("Use Listen in a world, Connect from here."), left + PANEL_WIDTH / 2, top + 260, 0xFFB8B8B8);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int left) {
        int y = rowsTop();
        for (int index = 0; index < ROW_COUNT; index++) {
            int rowY = y + index * ROW_HEIGHT;
            int color = index % 2 == 0 ? 0xFF3F3F3F : 0xFF383838;
            graphics.fill(left + 24, rowY - 2, left + PANEL_WIDTH - 24, rowY + 23, color);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int left, int top) {
        String selected = this.selectedPeer == null ? "No friend selected" : "Selected: " + this.selectedName;
        graphics.text(this.font, fit(selected, PANEL_WIDTH - 60), left + 30, top + PANEL_HEIGHT - 56, 0xFFCFCFCF);
    }

    private void drawEmptyScene(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 12, y + 58, x + 138, y + 74, 0xFF5FA236);
        graphics.fill(x + 12, y + 74, x + 58, y + 90, 0xFF6B4A2E);
        graphics.fill(x + 14, y + 76, x + 56, y + 88, 0xFF7C5432);
        graphics.fill(x + 72, y + 74, x + 136, y + 84, 0xFF7C5432);
        graphics.fill(x + 76, y + 70, x + 84, y + 74, 0xFF4E8A2B);
        graphics.fill(x + 122, y + 66, x + 138, y + 74, 0xFF5FA236);
        graphics.fill(x + 50, y + 24, x + 88, y + 58, 0xFF2E8F2E);
        graphics.fill(x + 38, y + 38, x + 100, y + 62, 0xFF36A832);
        graphics.fill(x + 62, y + 50, x + 72, y + 80, 0xFF7A5433);
        graphics.fill(x + 65, y + 54, x + 76, y + 65, 0xFF94693B);
        graphics.outline(x + 65, y + 54, 11, 11, 0xFF5D3B20);
        graphics.fill(x + 98, y + 66, x + 120, y + 80, 0xFFF47A21);
        graphics.fill(x + 104, y + 60, x + 116, y + 70, 0xFFFF8B2B);
        graphics.fill(x + 116, y + 64, x + 124, y + 72, 0xFFFFFFFF);
        graphics.fill(x + 100, y + 72, x + 118, y + 80, 0xFFFFFFFF);
        graphics.fill(x + 112, y + 62, x + 116, y + 66, 0xFF1A1A1A);
    }

    private List<FriendDto> visibleRows() {
        if (this.activeTab == Tab.FRIENDS) {
            return this.friendData.friends().stream().limit(ROW_COUNT).toList();
        }
        List<FriendDto> requests = new ArrayList<>();
        requests.addAll(this.friendData.incomingRequests());
        requests.addAll(this.friendData.outgoingRequests());
        return requests.stream().limit(ROW_COUNT).toList();
    }

    private Component requestsTitle() {
        int count = this.friendData.incomingRequests().size() + this.friendData.outgoingRequests().size();
        return Component.literal("Requests (" + count + ")");
    }

    private String displayName(FriendDto friend) {
        String name = friend.name();
        return name == null || name.isBlank() ? "Unknown" : fit(name, 150);
    }

    private String shortId(UUID uuid) {
        String value = uuid.toString();
        return value.substring(0, 8) + "...";
    }

    private String fit(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        return this.font.plainSubstrByWidth(value, Math.max(10, maxWidth - this.font.width("..."))) + "...";
    }

    private String userMessage(Throwable cause) {
        if (cause instanceof OfficialFriendsException friendsException) {
            return friendsException.userMessage();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return Math.max(58, (this.height - PANEL_HEIGHT) / 2 + 22);
    }

    private int rowsTop() {
        return panelTop() + 108;
    }

    private enum Tab {
        FRIENDS,
        REQUESTS
    }
}
