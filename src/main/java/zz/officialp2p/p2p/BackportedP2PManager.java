package zz.officialp2p.p2p;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zz.officialp2p.friends.OfficialFriendsClient;

import java.net.ProxySelector;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackportedP2PManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackportedP2PManager.class);

    private final Minecraft minecraft;
    private final OfficialFriendsClient friendsClient;
    private final AtomicBoolean closed = new AtomicBoolean();

    public BackportedP2PManager(Minecraft minecraft, User user) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.friendsClient = new OfficialFriendsClient(user.getAccessToken(), ProxySelector.getDefault());
    }

    public OfficialFriendsClient friendsClient() {
        return friendsClient;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("P2P manager is closed");
        }
    }

    public void startSignaling() {
        ensureOpen();
        LOGGER.info("Signaling backport is not wired yet; next step is WebRTC transport");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOGGER.info("Closed backported P2P manager for {}", minecraft.getUser().getName());
        }
    }
}
