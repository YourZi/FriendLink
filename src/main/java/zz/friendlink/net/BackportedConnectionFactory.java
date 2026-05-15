package zz.friendlink.net;

import io.netty.channel.Channel;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public final class BackportedConnectionFactory {
    private static final DefaultEventLoopGroup EVENT_LOOP_GROUP = new DefaultEventLoopGroup(1);
    private static final Field CHANNEL_FIELD = findField("channel");
    private static final Field ADDRESS_FIELD = findField("address");
    private static final SocketAddress FALLBACK_REMOTE_ADDRESS = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);

    private BackportedConnectionFactory() {
    }

    public static Connection fromChannel(Channel channel, PacketFlow packetFlow) {
        Connection connection = new Connection(packetFlow);

        channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30));
        setupProtocols(connection, channel.pipeline(), packetFlow);
        EVENT_LOOP_GROUP.register(channel).syncUninterruptibly();

        setField(CHANNEL_FIELD, connection, channel);
        SocketAddress remoteAddress = channel.remoteAddress();
        setField(ADDRESS_FIELD, connection, remoteAddress == null ? FALLBACK_REMOTE_ADDRESS : remoteAddress);
        return connection;
    }

    private static void setupProtocols(Connection connection, io.netty.channel.ChannelPipeline pipeline, PacketFlow flow) {
        Connection.configureSerialization(pipeline, flow, false, null);
        connection.configurePacketHandler(pipeline);
    }

    private static Field findField(String name) {
        try {
            Field field = Connection.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void setField(Field field, Connection connection, Object value) {
        try {
            field.set(connection, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to set Connection field " + field.getName(), exception);
        }
    }
}
