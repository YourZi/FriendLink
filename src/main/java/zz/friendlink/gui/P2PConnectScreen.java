package zz.friendlink.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import zz.friendlink.FriendLinkClient;
import zz.friendlink.assets.FriendLinkAssets;
import zz.friendlink.friends.OfficialFriendsClient;
import zz.friendlink.friends.OfficialFriendsException;
import zz.friendlink.friends.model.FriendData;
import zz.friendlink.friends.model.JoinInfoUpdate;
import zz.friendlink.friends.model.PresenceResponse;
import zz.friendlink.friends.model.PresenceStatusDto;
import zz.friendlink.friends.model.FriendDto;
import zz.friendlink.p2p.HostPresencePublisher;
import zz.friendlink.i18n.P2PTexts;
import zz.friendlink.util.Uuids;

import java.net.ProxySelector;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class P2PConnectScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/background.png");
    private static final ResourceLocation ILLUSTRATION_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/illustrations_00.png");
    private static final ResourceLocation INVITE_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/multiplayer/invite.png");
    private static final ResourceLocation REMOVE_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/remove.png");
    private static final ResourceLocation ACCEPT_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/accept.png");
    private static final ResourceLocation REJECT_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/reject.png");
    private static final ResourceLocation CANCEL_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/cancel.png");
    private static final ResourceLocation JOIN_REQUEST_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("friendlink", "textures/gui/sprites/friends/multiplayer/join_request.png");
    private static final int MAX_PANEL_WIDTH = 300;
    private static final int MAX_PANEL_HEIGHT = 300;
    private static final int MIN_PANEL_WIDTH = 236;
    private static final int MIN_PANEL_HEIGHT = 220;
    private static final int PANEL_TEXTURE_WIDTH = 236;
    private static final int PANEL_TEXTURE_HEIGHT = 34;
    private static final int PANEL_BORDER = 8;
    private static final int MAX_RENDER_ROWS = 8;
    private static final int FRIEND_ROW_HEIGHT = 40;
    private static final int REQUEST_ROW_HEIGHT = 24;
    private static final int SKIN_SIZE = 32;
    private static final int CONTENT_INSET = 12;
    private static final int ROW_INSET = 6;
    private static final int ACTION_BUTTON_SIZE = 22;
    private static final int ACTION_ICON_GAP = 4;
    private static final int ACTION_ROW_GAP = 8;
    private static final int ACTION_RIGHT_INSET = 2;
    private static final int SCROLL_STEP = 6;
    private static final long SUCCESS_REFRESH_COOLDOWN_MS = 10_000L;
    private static final long FAILURE_REFRESH_COOLDOWN_MS = 20_000L;
    private static FriendData cachedFriendData = FriendData.empty();
    private static PresenceResponse cachedPresence = PresenceResponse.empty();
    private static Component cachedStatus = P2PTexts.c("status.ready");
    private static OfficialFriendsClient cachedFriendsClient;
    private static String cachedUserKey = "";
    private static long nextFriendsFetchAt;

    private final Screen parent;
    private final List<Button> rowButtons = new ArrayList<>();
    private final List<Button> rowInviteButtons = new ArrayList<>();
    private final List<Button> rowRemoveButtons = new ArrayList<>();
    private static final Map<UUID, CompletableFuture<PlayerSkin>> SKIN_LOOKUP = new ConcurrentHashMap<>();
    private EditBox profileBox;
    private Button friendsTab;
    private Button requestsTab;
    private Button addButton;
    private FriendData friendData = FriendData.empty();
    private PresenceResponse presence = PresenceResponse.empty();
    private Tab activeTab = Tab.FRIENDS;
    private UUID selectedPeer;
    private String selectedName = "";
    private Component status = P2PTexts.c("status.ready");
    private boolean loadingFriends;
    private boolean closed;
    private int scrollPixels;

    public P2PConnectScreen(Screen parent) {
        super(P2PTexts.c("title.friends"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FriendLinkAssets.ensureInitialized();
        this.closed = false;
        this.rowButtons.clear();
        this.rowInviteButtons.clear();
        this.rowRemoveButtons.clear();
        ensureUserCache(this.minecraft.getUser());

        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int tabGap = 2;
        int tabWidth = (panelWidth - tabGap) / 2;

        this.friendsTab = this.addRenderableWidget(Button.builder(P2PTexts.c("title.friends"), button -> setTab(Tab.FRIENDS))
            .bounds(left, top - 24, tabWidth, 20)
            .build());
        this.requestsTab = this.addRenderableWidget(Button.builder(requestsTitle(), button -> setTab(Tab.REQUESTS))
            .bounds(left + tabWidth + tabGap, top - 24, tabWidth, 20)
            .build());

        this.profileBox = new EditBox(this.font, left + 20, top + 20, panelWidth - 62, 16, P2PTexts.c("input.player_or_host"));
        this.profileBox.setMaxLength(64);
        this.profileBox.setHint(P2PTexts.c("input.player_or_host"));
        this.profileBox.setBordered(true);
        this.profileBox.setTextColor(0xFFE2E2E2);
        this.profileBox.setTextColorUneditable(0xFFE2E2E2);
        this.addRenderableWidget(this.profileBox);

        this.addButton = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> addFriend())
            .bounds(left + panelWidth - 34, top + 18, 20, 20)
            .build());

        for (int index = 0; index < MAX_RENDER_ROWS; index++) {
            int row = index;
            Button button = this.addRenderableWidget(new SilentButton(rowLeft(), rowsTop() + row * rowHeight(), rowContentWidth(), friendRowHeight(), Component.empty(), ignored -> selectVisibleRow(row)));
            this.rowButtons.add(button);
            this.rowInviteButtons.add(this.addRenderableWidget(new ActionIconButton(left, rowsTop() + row * rowHeight(), row, true, ignored -> inviteVisibleRow(row))));
            this.rowRemoveButtons.add(this.addRenderableWidget(new ActionIconButton(left, rowsTop() + row * rowHeight(), row, false, ignored -> removeVisibleRow(row))));
        }

        this.friendData = cachedFriendData;
        this.presence = cachedPresence;
        this.status = cachedStatus;
        updateWidgets();
        refreshFriends(false);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int left = panelLeft();
        int top = panelTop();
        drawPanel(guiGraphics, left, top);
        drawHeader(guiGraphics, left, top);
        drawContent(guiGraphics, left, top);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.closed = true;
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.closed = true;
    }

    private void setTab(Tab tab) {
        this.activeTab = tab;
        this.selectedPeer = null;
        this.selectedName = "";
        this.scrollPixels = 0;
        updateWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<FriendDto> rows = allRows();
        int maxScroll = maxScrollPixels(rows.size());
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int delta = scrollY < 0 ? SCROLL_STEP : -SCROLL_STEP;
        int nextScroll = Math.max(0, Math.min(maxScroll, this.scrollPixels + delta));
        if (nextScroll == this.scrollPixels) {
            return true;
        }
        this.scrollPixels = nextScroll;
        updateWidgets();
        return true;
    }

    private void refreshFriends(boolean manual) {
        Minecraft client = Minecraft.getInstance();
        User user = client.getUser();
        long now = System.currentTimeMillis();
        if (now < nextFriendsFetchAt) {
            updateWidgets();
            return;
        }

        this.loadingFriends = true;
        this.status = P2PTexts.c("status.loading_friends");
        cachedStatus = this.status;
        updateWidgets();

        CompletableFuture
            .supplyAsync(() -> {
                OfficialFriendsClient friendsClient = friendsClient(user);
                FriendData data = friendsClient.getFriendData();
                PresenceResponse presence = fetchPresence(client, user, friendsClient);
                return new RefreshSnapshot(data, presence);
            })
            .whenComplete((data, throwable) -> client.execute(() -> {
                if (!isCurrent(client)) {
                    return;
                }
                this.loadingFriends = false;
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = P2PTexts.c("status.friends_failed", userMessage(cause));
                    cachedStatus = this.status;
                    nextFriendsFetchAt = System.currentTimeMillis() + FAILURE_REFRESH_COOLDOWN_MS;
                    FriendLinkClient.LOGGER.warn("FriendLink friends UI refresh failed", cause);
                    updateWidgets();
                    return;
                }
                RefreshSnapshot snapshot = data == null ? new RefreshSnapshot(FriendData.empty(), PresenceResponse.empty()) : data;
                this.friendData = snapshot.friendData();
                this.presence = snapshot.presence();
                cachedFriendData = this.friendData;
                cachedPresence = this.presence;
                this.status = P2PTexts.c("status.friends_loaded");
                cachedStatus = this.status;
                nextFriendsFetchAt = System.currentTimeMillis() + SUCCESS_REFRESH_COOLDOWN_MS;
                updateWidgets();
            }));
    }

    private void addFriend() {
        Minecraft client = Minecraft.getInstance();
        String raw = this.profileBox.getValue().trim();
        if (raw.isBlank()) {
            this.status = P2PTexts.c("status.type_player_name");
            return;
        }
        try {
            this.selectedPeer = Uuids.parseFlexible(raw);
            this.selectedName = "";
            this.status = P2PTexts.c("status.selected_host");
            updateWidgets();
            return;
        } catch (IllegalArgumentException ignored) {
        }

        this.addButton.active = false;
        this.status = P2PTexts.c("status.sending_friend_request");
        CompletableFuture
            .supplyAsync(() -> friendsClient(client.getUser()).addFriendByName(raw))
            .whenComplete((data, throwable) -> client.execute(() -> {
                if (!isCurrent(client)) {
                    return;
                }
                this.addButton.active = true;
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = P2PTexts.c("status.friend_action_failed", userMessage(cause));
                    FriendLinkClient.LOGGER.warn("FriendLink friend action failed", cause);
                    return;
                }
                this.friendData = data == null ? FriendData.empty() : data;
                cachedFriendData = this.friendData;
                this.status = P2PTexts.c("status.friend_request_updated");
                cachedStatus = this.status;
                updateWidgets();
            }));
    }

    private void removeSelectedFriend() {
        if (this.selectedPeer == null) {
            this.status = P2PTexts.c("status.no_friend_selected");
            return;
        }
        updateFriendData(P2PTexts.c("status.friend_removing"), P2PTexts.c("status.friend_removed"),
            () -> friendsClient(this.minecraft.getUser()).removeFriend(this.selectedPeer));
    }

    private void updateRequest(Component pendingStatus, Supplier<FriendData> action) {
        updateFriendData(pendingStatus, P2PTexts.c("status.request_updated"), action);
    }

    private void inviteVisibleRow(int row) {
        List<FriendDto> rows = visibleRows();
        if (row < 0 || row >= rows.size()) {
            return;
        }
        FriendDto friend = rows.get(row);
        if (this.activeTab != Tab.FRIENDS) {
            if (this.friendData.incomingRequests().stream().anyMatch(entry -> entry.profileId().equals(friend.profileId()))) {
                this.selectedPeer = friend.profileId();
                this.selectedName = friend.name() == null ? "" : friend.name();
                updateRequest(P2PTexts.c("status.request_accepting"),
                    () -> friendsClient(this.minecraft.getUser()).acceptFriendRequest(this.selectedPeer));
            }
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Button inviteButton = this.rowInviteButtons.get(row);
        inviteButton.active = false;

        if (isFriendHosting(friend)) {
            PresenceStatusDto presence = presenceFor(friend.profileId());
            UUID pmid = presence != null ? presence.pmid() : null;
            if (pmid == null) {
                inviteButton.active = true;
                return;
            }
            P2PUiActions.connect(client, pmid, this::setStatus)
                .whenComplete((ignored, throwable) -> client.execute(() -> {
                    if (!isCurrent(client)) {
                        return;
                    }
                    inviteButton.active = true;
                    updateWidgets();
                }));
        } else if (isInvitedByFriend(friend)) {
            PresenceStatusDto presence = presenceFor(friend.profileId());
            UUID pmid = presence != null ? presence.pmid() : null;
            if (pmid == null) {
                inviteButton.active = true;
                return;
            }
            P2PUiActions.connect(client, pmid, this::setStatus)
                .whenComplete((ignored, throwable) -> client.execute(() -> {
                    if (!isCurrent(client)) {
                        return;
                    }
                    inviteButton.active = true;
                    updateWidgets();
                }));
        } else {
            P2PUiActions.invite(client, friend.profileId(), this::setStatus)
                .whenComplete((ignored, throwable) -> client.execute(() -> {
                    if (!isCurrent(client)) {
                        return;
                    }
                    inviteButton.active = true;
                    updateWidgets();
                }));
        }
    }

    private void removeVisibleRow(int row) {
        List<FriendDto> rows = visibleRows();
        if (row < 0 || row >= rows.size()) {
            return;
        }
        FriendDto friend = rows.get(row);
        if (this.activeTab == Tab.FRIENDS && isInvitedByFriend(friend)) {
            this.setStatus(P2PTexts.s("status.invite_declined"));
            Button rejectButton = this.rowRemoveButtons.get(row);
            rejectButton.active = false;
            Button acceptButton = this.rowInviteButtons.get(row);
            acceptButton.active = false;
            return;
        }
        if (this.activeTab != Tab.FRIENDS) {
            this.selectedPeer = friend.profileId();
            this.selectedName = friend.name() == null ? "" : friend.name();
            if (this.friendData.incomingRequests().stream().anyMatch(entry -> entry.profileId().equals(friend.profileId()))) {
                updateRequest(P2PTexts.c("status.request_declining"),
                    () -> friendsClient(this.minecraft.getUser()).declineFriendRequest(this.selectedPeer));
            } else {
                updateRequest(P2PTexts.c("status.request_revoking"),
                    () -> friendsClient(this.minecraft.getUser()).revokeFriendRequest(this.selectedPeer));
            }
            return;
        }
        this.selectedPeer = friend.profileId();
        this.selectedName = friend.name() == null ? "" : friend.name();
        String name = this.selectedName.isBlank() ? P2PTexts.s("ui.unknown_player") : this.selectedName;
        this.minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    removeSelectedFriend();
                }
                this.minecraft.setScreen(this);
            },
            P2PTexts.c("confirm.remove_title"),
            P2PTexts.c("confirm.remove_message", name),
            P2PTexts.c("confirm.remove_yes"),
            P2PTexts.c("confirm.remove_no")
        ));
    }

    private void updateFriendData(Component pendingStatus, Component successStatus, Supplier<FriendData> action) {
        Minecraft client = Minecraft.getInstance();
        this.addButton.active = false;
        this.status = pendingStatus;
        CompletableFuture
            .supplyAsync(action)
            .whenComplete((data, throwable) -> client.execute(() -> {
                if (!isCurrent(client)) {
                    return;
                }
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = P2PTexts.c("status.friend_action_failed", userMessage(cause));
                    FriendLinkClient.LOGGER.warn("FriendLink friend request update failed", cause);
                    updateWidgets();
                    return;
                }
                this.friendData = data == null ? FriendData.empty() : data;
                cachedFriendData = this.friendData;
                this.selectedPeer = null;
                this.selectedName = "";
                this.status = successStatus;
                cachedStatus = this.status;
                updateWidgets();
            }));
    }

    private void selectVisibleRow(int row) {
        List<FriendDto> rows = visibleRows();
        if (row < 0 || row >= rows.size()) {
            return;
        }
        FriendDto friend = rows.get(row);
        this.selectedPeer = friend.profileId();
        this.selectedName = friend.name() == null ? "" : friend.name();
        this.profileBox.setValue(this.selectedName);
        this.status = P2PTexts.c("status.selected", displayName(friend));
        updateWidgets();
    }

    private void updateWidgets() {
        if (this.friendsTab == null) {
            return;
        }
        clampScrollOffset();
        this.friendsTab.active = this.activeTab != Tab.FRIENDS;
        this.requestsTab.active = this.activeTab != Tab.REQUESTS;
        this.requestsTab.setMessage(requestsTitle());
        this.addButton.active = !this.loadingFriends;

        List<FriendDto> rows = visibleRows();
        int rowHeight = rowHeight();
        int pixelRemainder = this.scrollPixels % rowHeight;
        int listTop = rowsTop();
        int listBottom = listTop + visibleRowCount() * rowHeight;
        boolean canInvite = canInviteToHostedWorld();
        for (int index = 0; index < this.rowButtons.size(); index++) {
            Button button = this.rowButtons.get(index);
            Button inviteButton = this.rowInviteButtons.get(index);
            Button removeButton = this.rowRemoveButtons.get(index);
            boolean visible = index < rows.size();
            int rowY = listTop + index * rowHeight - pixelRemainder;
            boolean insideList = rowY >= listTop && rowY + rowHeight <= listBottom;
            button.setX(rowLeft());
            button.setWidth(rowContentWidth());
            button.setY(rowY);
            button.visible = visible && insideList;
            button.active = visible && insideList;
            button.setHeight(rowHeight);
            button.setMessage(Component.empty());

            boolean showFriendActions = false;
            boolean online = false;
            boolean hosting = false;
            boolean invited = false;
            UUID rowProfileId = null;
            if (visible) {
                FriendDto friend = rows.get(index);
                rowProfileId = friend.profileId();
                online = isFriendOnline(friend);
                hosting = isFriendHosting(friend);
                invited = isInvitedByFriend(friend);
                showFriendActions = true;
                removeButton.setX(actionButtonsLeft() + ACTION_BUTTON_SIZE + ACTION_ICON_GAP);
                removeButton.setY(actionButtonsTop(rowY));
                inviteButton.setX(actionButtonsLeft());
                inviteButton.setY(actionButtonsTop(rowY));
            }
            UUID requestProfileId = rowProfileId;
            boolean incomingRequest = visible && this.activeTab == Tab.REQUESTS && requestProfileId != null && this.friendData.incomingRequests().stream()
                .anyMatch(entry -> entry.profileId().equals(requestProfileId));
            boolean outgoingRequest = visible && this.activeTab == Tab.REQUESTS && requestProfileId != null && this.friendData.outgoingRequests().stream()
                .anyMatch(entry -> entry.profileId().equals(requestProfileId));
            removeButton.visible = visible && insideList && showFriendActions && !this.loadingFriends;
            removeButton.active = visible && insideList && showFriendActions && !this.loadingFriends;
            inviteButton.visible = visible && insideList && (
                this.activeTab == Tab.FRIENDS ? ((online && canInvite) || hosting || invited) : incomingRequest
            ) && !this.loadingFriends;
            inviteButton.active = inviteButton.visible && !this.loadingFriends;
            if (this.activeTab == Tab.FRIENDS && invited) {
                removeButton.visible = visible && insideList && !this.loadingFriends;
                removeButton.active = visible && insideList && !this.loadingFriends;
            }
            if (outgoingRequest) {
                inviteButton.visible = false;
                inviteButton.active = false;
            }
        }
    }

    private void setStatus(String message) {
        if (this.closed) {
            return;
        }
        this.status = Component.literal(message);
    }

    private void drawPanel(GuiGraphics guiGraphics, int left, int top) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        drawNineSlice(guiGraphics, PANEL_TEXTURE, left, top, panelWidth, panelHeight, PANEL_TEXTURE_WIDTH, PANEL_TEXTURE_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(contentLeft(), top + 62, contentRight(), contentBottom(), 0xFF2F2F2F);
        drawOutline(guiGraphics, contentLeft(), top + 62, contentWidth(), contentBottom() - top - 62, 0xFF6A6A6A);
    }

    private void drawHeader(GuiGraphics guiGraphics, int left, int top) {
        guiGraphics.drawString(this.font, P2PTexts.s("ui.profile", this.minecraft.getUser().getName()), left + 16, top + 43, 0xFFD8D8D8);
        guiGraphics.fill(contentLeft(), top + 56, contentRight(), top + 57, 0xFF6A6A6A);
    }

    private void drawContent(GuiGraphics guiGraphics, int left, int top) {
        List<FriendDto> rows = allRows();
        if (this.loadingFriends) {
            guiGraphics.drawCenteredString(this.font, P2PTexts.c("ui.loading_friends"), left + panelWidth() / 2, top + 110, 0xFFFFFFFF);
            return;
        }
        if (!rows.isEmpty()) {
            drawRows(guiGraphics, left);
            drawScrollHint(guiGraphics, left, top, rows.size());
            drawFailureStatus(guiGraphics, left, top);
            return;
        }

        int panelWidth = panelWidth();
        drawEmptyScene(guiGraphics, left + panelWidth / 2 - 46, top + 70);
        drawFailureStatus(guiGraphics, left, top);
    }

    private void drawRows(GuiGraphics guiGraphics, int left) {
        List<FriendDto> rows = visibleRows();
        int rowHeight = rowHeight();
        int pixelRemainder = this.scrollPixels % rowHeight;
        int listTop = rowsTop();
        int listBottom = listTop + visibleRowCount() * rowHeight;
        for (int index = 0; index < rows.size() && index < this.rowButtons.size(); index++) {
            FriendDto friend = rows.get(index);
            Button button = this.rowButtons.get(index);
            int rowY = listTop + index * rowHeight - pixelRemainder;
            if (rowY < listTop || rowY + rowHeight > listBottom) {
                continue;
            }
            boolean selected = friend.profileId().equals(this.selectedPeer);
            guiGraphics.fill(rowLeft(), rowY, rowRight(), rowY + rowHeight - 1, selected ? 0x664D6F91 : 0x33222222);
            guiGraphics.fill(rowLeft(), rowY + rowHeight - 1, rowRight(), rowY + rowHeight, 0x66333333);
            if (this.activeTab == Tab.FRIENDS) {
                drawFriendRow(guiGraphics, friend, rowY, selected);
            } else {
                drawRequestRow(guiGraphics, friend, rowY, selected);
            }
        }
    }

    private void drawEmptyScene(GuiGraphics guiGraphics, int x, int y) {
        int center = panelLeft() + panelWidth() / 2;
        int imageX = center - 64;
        int imageY = y + 10;
        guiGraphics.blit(ILLUSTRATION_TEXTURE, imageX, imageY, 0, 0, 128, 48, 128, 48);
        guiGraphics.drawCenteredString(this.font, activeTab == Tab.FRIENDS ? P2PTexts.c("ui.empty_friends") : P2PTexts.c("ui.empty_requests"), center, imageY + 66, 0xFFDADADA);
        guiGraphics.drawCenteredString(this.font, P2PTexts.c("input.player_or_host"), center, imageY + 82, 0xFFAFAFAF);
    }

    private List<FriendDto> visibleRows() {
        List<FriendDto> rows = allRows();
        int rowHeight = rowHeight();
        int from = Math.min(this.scrollPixels / rowHeight, rows.size());
        int to = Math.min(from + MAX_RENDER_ROWS, rows.size());
        return rows.subList(from, to);
    }

    private List<FriendDto> allRows() {
        if (this.activeTab == Tab.FRIENDS) {
            return this.friendData.friends();
        }
        List<FriendDto> requests = new ArrayList<>();
        requests.addAll(this.friendData.incomingRequests());
        requests.addAll(this.friendData.outgoingRequests());
        return requests;
    }

    private void clampScrollOffset() {
        this.scrollPixels = Math.max(0, Math.min(this.scrollPixels, maxScrollPixels(allRows().size())));
    }

    private int maxScrollPixels(int rowCount) {
        return Math.max(0, rowCount * rowHeight() - visibleRowCount() * rowHeight());
    }

    private void drawScrollHint(GuiGraphics guiGraphics, int left, int top, int rowCount) {
        if (rowCount <= visibleRowCount()) {
            return;
        }
        int trackTop = rowsTop();
        int trackHeight = visibleRowCount() * rowHeight() - 2;
        int trackLeft = left + panelWidth() - 18;
        int maxOffset = Math.max(1, maxScrollPixels(rowCount));
        int thumbHeight = Math.max(10, trackHeight * visibleRowCount() / rowCount);
        int thumbY = trackTop + (trackHeight - thumbHeight) * this.scrollPixels / maxOffset;
        guiGraphics.fill(trackLeft, trackTop, trackLeft + 4, trackTop + trackHeight, 0xFF181818);
        guiGraphics.fill(trackLeft + 1, thumbY, trackLeft + 3, thumbY + thumbHeight, 0xFFD9D9D9);
    }

    private Component requestsTitle() {
        int count = this.friendData.incomingRequests().size() + this.friendData.outgoingRequests().size();
        return P2PTexts.c("ui.requests", count);
    }

    private boolean isSelectedIncomingRequest() {
        return this.selectedPeer != null && this.friendData.incomingRequests().stream()
            .anyMatch(friend -> friend.profileId().equals(this.selectedPeer));
    }

    private boolean isSelectedOutgoingRequest() {
        return this.selectedPeer != null && this.friendData.outgoingRequests().stream()
            .anyMatch(friend -> friend.profileId().equals(this.selectedPeer));
    }

    private boolean isSelectedFriend() {
        return this.selectedPeer != null && this.friendData.friends().stream()
            .anyMatch(friend -> friend.profileId().equals(this.selectedPeer));
    }

    private String displayName(FriendDto friend) {
        String name = friend.name();
        return name == null || name.isBlank() ? P2PTexts.s("ui.unknown_player") : fit(name, 130);
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

    private PresenceResponse fetchPresence(Minecraft client, User user, OfficialFriendsClient friendsClient) {
        var manager = FriendLinkClient.experimentalManagerIfPresent();
        if (manager != null && manager.isHostedPresenceActive()) {
            return manager.refreshHostedPresence().join();
        }
        return friendsClient.presence(HostPresencePublisher.currentStatus(client), JoinInfoUpdate.emptyInvites());
    }

    private boolean isFailureStatus() {
        return this.status.getString().contains(P2PTexts.s("word.failed"));
    }

    private void drawFailureStatus(GuiGraphics guiGraphics, int left, int top) {
        if (!isFailureStatus()) {
            return;
        }
        int centerX = left + panelWidth() / 2;
        int y = panelTop() + panelHeight() - 34;
        guiGraphics.drawCenteredString(this.font, fit(this.status.getString(), panelWidth() - 32), centerX, y, 0xFFAA0000);
    }

    private void drawFriendRow(GuiGraphics guiGraphics, FriendDto friend, int rowY, boolean selected) {
        int avatarX = rowLeft() + 4;
        int avatarY = rowY + 4;
        int textX = avatarX + SKIN_SIZE + 8;
        PlayerFaceRenderer.draw(guiGraphics, skinFor(friend), avatarX, avatarY, SKIN_SIZE);
        guiGraphics.drawString(this.font, fit(displayName(friend), textWidth()), textX, rowY + 6, selected ? 0xFFFFFFFF : 0xFFF0F0F0, false);
        boolean online = isFriendOnline(friend);
        String statusText = friendStatusText(friend);
        guiGraphics.drawString(this.font, statusText, textX, rowY + 20, online ? 0xFF33DD33 : 0xFFB3B3B3, false);
    }

    private void drawRequestRow(GuiGraphics guiGraphics, FriendDto friend, int rowY, boolean selected) {
        int avatarX = rowLeft() + 4;
        int avatarY = rowY + 4;
        int textX = avatarX + 18 + 8;
        PlayerFaceRenderer.draw(guiGraphics, skinFor(friend), avatarX, avatarY, 18);
        guiGraphics.drawString(this.font, fit(displayName(friend), textWidth()), textX, rowY + 4, selected ? 0xFFFFFFFF : 0xFFF0F0F0, false);
        guiGraphics.drawString(this.font, requestState(friend), textX, rowY + 14, 0xFFB3B3B3, false);
    }

    private ResourceLocation actionTexture(int rowIndex, boolean primary) {
        if (this.activeTab == Tab.FRIENDS) {
            if (primary) {
                FriendDto friend = friendAt(rowIndex);
                if (friend != null && isInvitedByFriend(friend)) {
                    return ACCEPT_ICON_TEXTURE;
                }
                if (friend != null && isFriendHosting(friend)) {
                    return JOIN_REQUEST_ICON_TEXTURE;
                }
                return INVITE_ICON_TEXTURE;
            }
            FriendDto friend = friendAt(rowIndex);
            if (friend != null && isInvitedByFriend(friend)) {
                return REJECT_ICON_TEXTURE;
            }
            return REMOVE_ICON_TEXTURE;
        }
        if (primary) {
            return ACCEPT_ICON_TEXTURE;
        }
        FriendDto friend = friendAt(rowIndex);
        return friend != null && this.friendData.incomingRequests().stream().anyMatch(entry -> entry.profileId().equals(friend.profileId()))
            ? REJECT_ICON_TEXTURE
            : CANCEL_ICON_TEXTURE;
    }

    private int actionTextureWidth(ResourceLocation texture) {
        if (texture.equals(INVITE_ICON_TEXTURE)) {
            return 12;
        }
        if (texture.equals(JOIN_REQUEST_ICON_TEXTURE)) {
            return 18;
        }
        if (texture.equals(REMOVE_ICON_TEXTURE)) {
            return 14;
        }
        return 12;
    }

    private int actionTextureHeight(ResourceLocation texture) {
        if (texture.equals(INVITE_ICON_TEXTURE)) {
            return 18;
        }
        if (texture.equals(JOIN_REQUEST_ICON_TEXTURE)) {
            return 18;
        }
        if (texture.equals(REMOVE_ICON_TEXTURE)) {
            return 13;
        }
        return 12;
    }

    private FriendDto friendAt(int index) {
        List<FriendDto> rows = visibleRows();
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private PlayerSkin skinFor(FriendDto friend) {
        GameProfile fallbackProfile = new GameProfile(friend.profileId(), friend.name());
        CompletableFuture<PlayerSkin> future = SKIN_LOOKUP.computeIfAbsent(friend.profileId(),
            ignored -> loadSkin(fallbackProfile));
        PlayerSkin skin = future.getNow(null);
        return skin != null ? skin : DefaultPlayerSkin.get(fallbackProfile);
    }

    private CompletableFuture<PlayerSkin> loadSkin(GameProfile fallbackProfile) {
        Minecraft client = Minecraft.getInstance();
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    var result = client.getMinecraftSessionService().fetchProfile(fallbackProfile.getId(), true);
                    if (result != null && result.profile() != null) {
                        return result.profile();
                    }
                } catch (RuntimeException exception) {
                    FriendLinkClient.LOGGER.debug("FriendLink skin profile lookup failed for {}", fallbackProfile.getId(), exception);
                }
                return fallbackProfile;
            })
            .thenCompose(profile -> client.getSkinManager().getOrLoad(profile))
            .exceptionally(throwable -> {
                FriendLinkClient.LOGGER.debug("FriendLink skin load failed for {}", fallbackProfile.getId(), throwable);
                return DefaultPlayerSkin.get(fallbackProfile);
            });
    }

    private boolean isFriendOnline(FriendDto friend) {
        PresenceStatusDto presence = presenceFor(friend.profileId());
        return presence != null && presence.status() != null && !presence.status().isBlank() && !"PLAYING_OFFLINE".equals(presence.status());
    }

    private String friendStatusText(FriendDto friend) {
        PresenceStatusDto presence = presenceFor(friend.profileId());
        if (presence == null || presence.status() == null || presence.status().isBlank()) {
            return P2PTexts.s("ui.offline");
        }
        String status = presence.status();
        if ("PLAYING_OFFLINE".equals(status)) {
            return P2PTexts.s("ui.offline");
        }
        if ("PLAYING_HOSTED_SERVER".equals(status)) {
            return P2PTexts.s("ui.status_hosting");
        }
        if ("ONLINE".equals(status)) {
            return P2PTexts.s("ui.status_playing");
        }
        return P2PTexts.s("ui.online");
    }

    private boolean isFriendHosting(FriendDto friend) {
        PresenceStatusDto presence = presenceFor(friend.profileId());
        return presence != null && "PLAYING_HOSTED_SERVER".equals(presence.status());
    }

    private boolean isInvitedByFriend(FriendDto friend) {
        PresenceStatusDto presence = presenceFor(friend.profileId());
        return presence != null
            && presence.joinInfo() != null
            && presence.joinInfo().invited();
    }

    private PresenceStatusDto presenceFor(UUID profileId) {
        for (PresenceStatusDto entry : this.presence.presence()) {
            if (profileId.equals(entry.profileId())) {
                return entry;
            }
        }
        return null;
    }

    private String requestState(FriendDto friend) {
        if (this.friendData.incomingRequests().stream().anyMatch(entry -> entry.profileId().equals(friend.profileId()))) {
            return P2PTexts.s("ui.incoming_request");
        }
        return P2PTexts.s("ui.outgoing_request");
    }

    private boolean canInviteToHostedWorld() {
        Minecraft client = Minecraft.getInstance();
        var manager = FriendLinkClient.experimentalManagerIfPresent();
        return this.activeTab == Tab.FRIENDS
            && client.level != null
            && manager != null
            && manager.isHostedPresenceActive();
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(44, (this.height - panelHeight()) / 2);
    }

    private int rowsTop() {
        return panelTop() + 68;
    }

    private int panelWidth() {
        return Math.max(MIN_PANEL_WIDTH, Math.min(MAX_PANEL_WIDTH, this.width - 36));
    }

    private int panelHeight() {
        return Math.max(MIN_PANEL_HEIGHT, Math.min(MAX_PANEL_HEIGHT, this.height - 88));
    }

    private int contentBottom() {
        return panelTop() + panelHeight() - 12;
    }

    private int contentLeft() {
        return panelLeft() + CONTENT_INSET;
    }

    private int contentRight() {
        return panelLeft() + panelWidth() - CONTENT_INSET;
    }

    private int contentWidth() {
        return panelWidth() - CONTENT_INSET * 2;
    }

    private int rowLeft() {
        return contentLeft() + ROW_INSET;
    }

    private int rowRight() {
        return contentRight() - ROW_INSET;
    }

    private int actionButtonsLeft() {
        return rowRight() - actionButtonsWidth() - ACTION_RIGHT_INSET;
    }

    private int rowWidth() {
        return rowRight() - rowLeft();
    }

    private int rowContentWidth() {
        return actionButtonsLeft() - rowLeft() - ACTION_ROW_GAP;
    }

    private int textWidth() {
        return rowContentWidth() - SKIN_SIZE - 12;
    }

    private int actionButtonsWidth() {
        return ACTION_BUTTON_SIZE * 2 + ACTION_ICON_GAP;
    }

    private int actionButtonsTop(int rowY) {
        if (this.activeTab == Tab.FRIENDS) {
            int avatarTop = rowY + 4;
            return avatarTop + (SKIN_SIZE - ACTION_BUTTON_SIZE) / 2;
        }
        return rowY + (rowHeight() - ACTION_BUTTON_SIZE) / 2;
    }

    private int rowHeight() {
        return this.activeTab == Tab.FRIENDS ? FRIEND_ROW_HEIGHT : REQUEST_ROW_HEIGHT;
    }

    private int friendRowHeight() {
        return FRIEND_ROW_HEIGHT;
    }

    private int visibleRowCount() {
        return Math.max(1, Math.min(MAX_RENDER_ROWS, (contentBottom() - rowsTop() - 8) / rowHeight()));
    }

    private static void drawNineSlice(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height, int textureWidth, int textureHeight, int border) {
        int centerWidth = Math.max(0, width - border * 2);
        int centerHeight = Math.max(0, height - border * 2);
        int srcCenterWidth = textureWidth - border * 2;
        int srcCenterHeight = textureHeight - border * 2;

        guiGraphics.blit(texture, x, y, 0, 0, border, border, textureWidth, textureHeight);
        guiGraphics.blit(texture, x + width - border, y, textureWidth - border, 0, border, border, textureWidth, textureHeight);
        guiGraphics.blit(texture, x, y + height - border, 0, textureHeight - border, border, border, textureWidth, textureHeight);
        guiGraphics.blit(texture, x + width - border, y + height - border, textureWidth - border, textureHeight - border, border, border, textureWidth, textureHeight);

        tileRegion(guiGraphics, texture, x + border, y, centerWidth, border, border, 0, srcCenterWidth, border, textureWidth, textureHeight);
        tileRegion(guiGraphics, texture, x + border, y + height - border, centerWidth, border, border, textureHeight - border, srcCenterWidth, border, textureWidth, textureHeight);
        tileRegion(guiGraphics, texture, x, y + border, border, centerHeight, 0, border, border, srcCenterHeight, textureWidth, textureHeight);
        tileRegion(guiGraphics, texture, x + width - border, y + border, border, centerHeight, textureWidth - border, border, border, srcCenterHeight, textureWidth, textureHeight);
        tileRegion(guiGraphics, texture, x + border, y + border, centerWidth, centerHeight, border, border, srcCenterWidth, srcCenterHeight, textureWidth, textureHeight);
    }

    private static void tileRegion(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height,
                                   int srcU, int srcV, int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        if (width <= 0 || height <= 0 || srcWidth <= 0 || srcHeight <= 0) {
            return;
        }
        for (int offsetY = 0; offsetY < height; offsetY += srcHeight) {
            int drawHeight = Math.min(srcHeight, height - offsetY);
            for (int offsetX = 0; offsetX < width; offsetX += srcWidth) {
                int drawWidth = Math.min(srcWidth, width - offsetX);
                guiGraphics.blit(texture, x + offsetX, y + offsetY, srcU, srcV, drawWidth, drawHeight, textureWidth, textureHeight);
            }
        }
    }

    private boolean isCurrent(Minecraft client) {
        return !this.closed && client.screen == this;
    }

    private static synchronized OfficialFriendsClient friendsClient(User user) {
        ensureUserCache(user);
        return cachedFriendsClient;
    }

    private static synchronized void ensureUserCache(User user) {
        String key = user.getProfileId() + "|" + user.getAccessToken();
        if (cachedFriendsClient != null && key.equals(cachedUserKey)) {
            return;
        }
        cachedUserKey = key;
        cachedFriendsClient = new OfficialFriendsClient(user.getAccessToken(), ProxySelector.getDefault());
        cachedFriendData = FriendData.empty();
        cachedPresence = PresenceResponse.empty();
        cachedStatus = P2PTexts.c("status.ready");
        nextFriendsFetchAt = 0L;
    }

    private record RefreshSnapshot(FriendData friendData, PresenceResponse presence) {
    }

    private enum Tab {
        FRIENDS,
        REQUESTS
    }

    private static final class SilentButton extends Button {
        private SilentButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        }
    }

    private final class ActionIconButton extends Button {
        private final int rowIndex;
        private final boolean primary;

        private ActionIconButton(int x, int y, int rowIndex, boolean primary, OnPress onPress) {
            super(x, y, ACTION_BUTTON_SIZE, ACTION_BUTTON_SIZE, Component.empty(), onPress, Button.DEFAULT_NARRATION);
            this.rowIndex = rowIndex;
            this.primary = primary;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
            ResourceLocation texture = actionTexture(this.rowIndex, this.primary);
            int iconWidth = actionTextureWidth(texture);
            int iconHeight = actionTextureHeight(texture);
            int iconX = this.getX() + (this.getWidth() - iconWidth) / 2;
            int iconY = this.getY() + (this.getHeight() - iconHeight) / 2;
            guiGraphics.blit(texture, iconX, iconY, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
        }
    }

}
