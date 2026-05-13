package zz.officialp2p.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import zz.officialp2p.OfficialP2PBackportClient;
import zz.officialp2p.util.Uuids;

import java.util.UUID;

public final class P2PConnectScreen extends Screen {
    private final Screen parent;
    private EditBox pmidBox;
    private Button listenButton;
    private Button connectButton;
    private Component status = Component.literal("Ready. " + OfficialP2PBackportClient.BUILD_MARKER);

    public P2PConnectScreen(Screen parent) {
        super(Component.literal("Official P2P " + OfficialP2PBackportClient.BUILD_MARKER));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int top = Math.max(40, this.height / 4);

        this.pmidBox = new EditBox(this.font, center - 150, top + 68, 300, 20, Component.literal("Host UUID"));
        this.pmidBox.setMaxLength(36);
        this.pmidBox.setHint(Component.literal("Host UUID"));
        this.addRenderableWidget(this.pmidBox);

        this.listenButton = this.addRenderableWidget(Button.builder(Component.literal("Listen"), button -> listen())
            .bounds(center - 150, top + 100, 145, 20)
            .build());

        this.connectButton = this.addRenderableWidget(Button.builder(Component.literal("Connect"), button -> connect())
            .bounds(center + 5, top + 100, 145, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.minecraft.setScreen(this.parent))
            .bounds(center - 100, top + 136, 200, 20)
            .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int top = Math.max(40, this.height / 4);
        this.extractMenuBackground(graphics);
        graphics.centeredText(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        graphics.centeredText(this.font, this.status, this.width / 2, top + 8, 0xFFFF55);
        graphics.centeredText(this.font, Component.literal("My UUID: " + this.minecraft.getUser().getProfileId()), this.width / 2, top + 30, 0xA0FFA0);
        graphics.centeredText(this.font, Component.literal("Host UUID"), this.width / 2, top + 54, 0xA0A0A0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void listen() {
        Minecraft client = Minecraft.getInstance();
        this.listenButton.active = false;
        P2PUiActions.listen(client, this::setStatus)
            .whenComplete((ignored, throwable) -> client.execute(() -> this.listenButton.active = true));
    }

    private void connect() {
        Minecraft client = Minecraft.getInstance();
        UUID peerPmid;
        String rawPeer = this.pmidBox.getValue().trim();
        if (rawPeer.isBlank()) {
            P2PUiActions.status(client, this::setStatus, "Host UUID is empty.");
            return;
        }
        try {
            peerPmid = Uuids.parseFlexible(rawPeer);
        } catch (IllegalArgumentException exception) {
            P2PUiActions.status(client, this::setStatus, "Invalid host UUID: " + rawPeer);
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

    private void setStatus(String message) {
        this.status = Component.literal(message);
    }
}
