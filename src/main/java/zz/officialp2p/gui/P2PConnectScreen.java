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
    private static final int MAX_PANEL_WIDTH = 340;
    private static final int MAX_PANEL_HEIGHT = 286;
    private static final int ROW_COUNT = 5;
    private static final int ROW_HEIGHT = 24;
    private static final long SUCCESS_REFRESH_COOLDOWN_MS = 20_000L;
    private static final long FAILURE_REFRESH_COOLDOWN_MS = 120_000L;
    private static FriendData cachedFriendData = FriendData.empty();
    private static Component cachedStatus = Component.literal("就绪 " + OfficialP2PBackportClient.BUILD_MARKER);
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
    private Component status = Component.literal("就绪 " + OfficialP2PBackportClient.BUILD_MARKER);
    private boolean loadingFriends;

    public P2PConnectScreen(Screen parent) {
        super(Component.literal("好友"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int tabWidth = (panelWidth - 28) / 2;

        this.friendsTab = this.addRenderableWidget(Button.builder(Component.literal("好友"), button -> setTab(Tab.FRIENDS))
            .bounds(left + 14, top - 32, tabWidth, 30)
            .build());
        this.requestsTab = this.addRenderableWidget(Button.builder(requestsTitle(), button -> setTab(Tab.REQUESTS))
            .bounds(left + 14 + tabWidth, top - 32, tabWidth, 30)
            .build());

        this.profileBox = new EditBox(this.font, left + 24, top + 18, panelWidth - 78, 20, Component.literal("输入玩家名或房主ID"));
        this.profileBox.setMaxLength(64);
        this.profileBox.setHint(Component.literal("输入玩家名或房主ID"));
        this.addRenderableWidget(this.profileBox);

        this.addButton = this.addRenderableWidget(Button.builder(Component.literal("+"), button -> addFriend())
            .bounds(left + panelWidth - 46, top + 18, 22, 20)
            .build());

        for (int index = 0; index < ROW_COUNT; index++) {
            int row = index;
            Button button = this.addRenderableWidget(Button.builder(Component.empty(), ignored -> selectVisibleRow(row))
                .bounds(left + 24, rowsTop() + row * ROW_HEIGHT, panelWidth - 48, 20)
                .build());
            this.rowButtons.add(button);
        }

        int bottom = top + panelHeight - 30;
        this.listenButton = this.addRenderableWidget(Button.builder(Component.literal("开房"), button -> listen())
            .bounds(left + 24, bottom, 64, 20)
            .build());
        this.connectButton = this.addRenderableWidget(Button.builder(Component.literal("加入"), button -> connect())
            .bounds(left + 94, bottom, 64, 20)
            .build());
        this.refreshButton = this.addRenderableWidget(Button.builder(Component.literal("刷新"), button -> refreshFriends(true))
            .bounds(left + 164, bottom, 64, 20)
            .build());
        this.copyIdButton = this.addRenderableWidget(Button.builder(Component.literal("我的ID"), button -> fillMyId())
            .bounds(left + 234, bottom, 70, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("返回"), button -> this.minecraft.setScreen(this.parent))
            .bounds(left + panelWidth - 58, top + panelHeight + 6, 50, 20)
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
            this.status = Component.literal((manual ? "刷新" : "自动刷新") + "冷却中：" + seconds + "秒");
            cachedStatus = this.status;
            updateWidgets();
            return;
        }

        this.loadingFriends = true;
        this.status = Component.literal("正在获取官方好友...");
        cachedStatus = this.status;
        updateWidgets();

        CompletableFuture
            .supplyAsync(() -> new OfficialFriendsClient(user.getAccessToken(), ProxySelector.getDefault()).getFriendData())
            .whenComplete((data, throwable) -> client.execute(() -> {
                this.loadingFriends = false;
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = Component.literal("好友获取失败：" + userMessage(cause));
                    cachedStatus = this.status;
                    nextFriendsFetchAt = System.currentTimeMillis() + FAILURE_REFRESH_COOLDOWN_MS;
                    OfficialP2PBackportClient.LOGGER.warn("Official friends UI refresh failed", cause);
                    updateWidgets();
                    return;
                }
                this.friendData = data == null ? FriendData.empty() : data;
                cachedFriendData = this.friendData;
                this.status = Component.literal("好友已加载");
                cachedStatus = this.status;
                nextFriendsFetchAt = System.currentTimeMillis() + SUCCESS_REFRESH_COOLDOWN_MS;
                updateWidgets();
            }));
    }

    private void addFriend() {
        Minecraft client = Minecraft.getInstance();
        String raw = this.profileBox.getValue().trim();
        if (raw.isBlank()) {
            this.status = Component.literal("先输入玩家名");
            return;
        }
        try {
            this.selectedPeer = Uuids.parseFlexible(raw);
            this.selectedName = "";
            this.status = Component.literal("已选择房主ID，点“加入”进入");
            updateWidgets();
            return;
        } catch (IllegalArgumentException ignored) {
        }

        this.addButton.active = false;
        this.status = Component.literal("正在发送好友请求...");
        CompletableFuture
            .supplyAsync(() -> {
                OfficialFriendsClient friends = new OfficialFriendsClient(client.getUser().getAccessToken(), ProxySelector.getDefault());
                return friends.putFriendAction(friendAction(raw));
            })
            .whenComplete((data, throwable) -> client.execute(() -> {
                this.addButton.active = true;
                if (throwable != null) {
                    Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                    this.status = Component.literal("好友操作失败：" + userMessage(cause));
                    OfficialP2PBackportClient.LOGGER.warn("Official friend action failed", cause);
                    return;
                }
                this.friendData = data == null ? FriendData.empty() : data;
                this.status = Component.literal("好友请求已更新");
                updateWidgets();
            }));
    }

    private FriendActionRequest friendAction(String raw) {
        return FriendActionRequest.addByName(raw);
    }

    private void listen() {
        Minecraft client = Minecraft.getInstance();
        this.listenButton.active = false;
        this.listenButton.setMessage(Component.literal("开房中..."));
        P2PUiActions.listen(client, this::setStatus)
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                this.listenButton.active = true;
                this.listenButton.setMessage(Component.literal("开房"));
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
                P2PUiActions.status(client, this::setStatus, "请选择好友，或输入有效的房主ID。");
                return;
            }
        }
        if (peerPmid == null) {
            P2PUiActions.status(client, this::setStatus, "请选择好友，或输入房主ID。");
            return;
        }

        this.connectButton.active = false;
        this.connectButton.setMessage(Component.literal("连接中..."));
        P2PUiActions.connect(client, peerPmid, this::setStatus)
            .whenComplete((ignored, throwable) -> client.execute(() -> {
                this.connectButton.active = true;
                this.connectButton.setMessage(Component.literal("加入"));
            }));
    }

    private void fillMyId() {
        this.profileBox.setValue(this.minecraft.getUser().getProfileId().toString());
        this.selectedPeer = null;
        this.selectedName = "";
        this.status = Component.literal("已填入你的ID");
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
        this.status = Component.literal("已选择：" + displayName(friend));
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
        int panelWidth = panelWidth();
        int tabWidth = (panelWidth - 28) / 2;
        int activeLeft = this.activeTab == Tab.FRIENDS ? left + 14 : left + 14 + tabWidth;
        graphics.fill(left + 14, top - 32, left + 14 + tabWidth * 2, top - 2, 0xFF1B1B1B);
        graphics.fill(activeLeft, top - 32, activeLeft + tabWidth, top - 2, 0xFF8E8E8E);
        graphics.outline(left + 14, top - 32, tabWidth, 30, 0xFF000000);
        graphics.outline(left + 14 + tabWidth, top - 32, tabWidth, 30, 0xFF000000);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, int left, int top) {
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        graphics.fill(left - 4, top - 4, left + panelWidth + 4, top + panelHeight + 4, 0xFFDDDDDD);
        graphics.fill(left - 2, top - 2, left + panelWidth + 2, top + panelHeight + 2, 0xFF000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF343434);
        graphics.outline(left, top, panelWidth, panelHeight, 0xFFFFFFFF);
        graphics.outline(left + 4, top + 4, panelWidth - 8, panelHeight - 8, 0xFF1C1C1C);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int left, int top) {
        int panelWidth = panelWidth();
        graphics.fill(left + 18, top + 12, left + panelWidth - 18, top + 58, 0xFF2A2A2A);
        graphics.text(this.font, "我的档案名：" + this.minecraft.getUser().getName(), left + 24, top + 42, 0xFFCFCFCF);
        int statusColor = this.status.getString().contains("失败") ? 0xFFFFFF55 : 0xFFCFCFCF;
        graphics.text(this.font, fit(this.status.getString(), panelWidth - 48), left + 24, top + 64, statusColor);
        graphics.fill(left + 8, top + 88, left + panelWidth - 8, top + 89, 0xFF1C1C1C);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int left, int top) {
        List<FriendDto> rows = visibleRows();
        if (this.loadingFriends) {
            graphics.centeredText(this.font, Component.literal("正在加载好友..."), left + panelWidth() / 2, top + 132, 0xFFFFFFFF);
            return;
        }
        if (!rows.isEmpty()) {
            drawRows(graphics, left);
            return;
        }

        int panelWidth = panelWidth();
        drawEmptyScene(graphics, left + panelWidth / 2 - 76, top + 96);
        if (this.activeTab == Tab.FRIENDS) {
            graphics.centeredText(this.font, Component.literal("添加后的好友会显示在这里"), left + panelWidth / 2, top + 196, 0xFFCFCFCF);
        } else {
            graphics.centeredText(this.font, Component.literal("好友请求会显示在这里"), left + panelWidth / 2, top + 196, 0xFFCFCFCF);
        }
        graphics.centeredText(this.font, Component.literal("房主点开房，加入方点加入"), left + panelWidth / 2, top + 218, 0xFFB8B8B8);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int left) {
        int y = rowsTop();
        for (int index = 0; index < ROW_COUNT; index++) {
            int rowY = y + index * ROW_HEIGHT;
            int color = index % 2 == 0 ? 0xFF3F3F3F : 0xFF383838;
            graphics.fill(left + 22, rowY - 2, left + panelWidth() - 22, rowY + 21, color);
        }
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int left, int top) {
        String selected = this.selectedPeer == null ? "未选择好友" : "已选择：" + this.selectedName;
        graphics.text(this.font, fit(selected, panelWidth() - 48), left + 24, top + panelHeight() - 50, 0xFFCFCFCF);
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
        return Component.literal("请求 (" + count + ")");
    }

    private String displayName(FriendDto friend) {
        String name = friend.name();
        return name == null || name.isBlank() ? "未知玩家" : fit(name, 130);
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
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(56, (this.height - panelHeight()) / 2 + 12);
    }

    private int rowsTop() {
        return panelTop() + 98;
    }

    private int panelWidth() {
        return Math.max(300, Math.min(MAX_PANEL_WIDTH, this.width - 92));
    }

    private int panelHeight() {
        return Math.max(238, Math.min(MAX_PANEL_HEIGHT, this.height - 144));
    }

    private enum Tab {
        FRIENDS,
        REQUESTS
    }
}
