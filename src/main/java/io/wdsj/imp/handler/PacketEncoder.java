package io.wdsj.imp.handler;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.PacketEventsImplHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.ReferenceCountUtil;
import io.wdsj.imp.impl.player.Player;
import io.wdsj.imp.impl.util.AdvancedList;

import java.util.ArrayList;
import java.util.List;

public class PacketEncoder extends MessageToByteEncoder<ByteBuf> {
    public final Player player;
    public User user;
    public final List<Runnable> queuedPostTasks = new ArrayList<>();

    public PacketEncoder(User user, Player player) {
        this.user = user;
        this.player = player;
    }
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        if (msg.isReadable()) {
            out.writeBytes(msg);
            PacketSendEvent sendEvent = PacketEventsImplHelper.handleClientBoundPacket(ctx.channel(), user, player, out, true);
            if (sendEvent.hasPostTasks()) {
                queuedPostTasks.addAll(sendEvent.getPostTasks());
            }
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        //This is netty code of the write method, and we are just "injecting" into it.
        ByteBuf buf = null;
        try {
            if (this.acceptOutboundMessage(msg)) {
                buf = this.allocateBuffer(ctx, (ByteBuf) msg, true);
                try {
                    this.encode(ctx, (ByteBuf) msg, buf);
                } finally {
                    ReferenceCountUtil.release(msg);
                    //Imp - Start
                    if (!queuedPostTasks.isEmpty()) {
                        List<Runnable> tasks = new AdvancedList<>(queuedPostTasks);
                        queuedPostTasks.clear();
                        promise.addListener(f -> {
                            for (Runnable task : tasks) {
                                task.run();
                            }
                        });
                    }
                    //Imp - End
                }
                if (buf.isReadable()) {
                    ctx.write(buf, promise);
                } else {
                    buf.release();
                    ctx.write(Unpooled.EMPTY_BUFFER, promise);
                }
                buf = null;
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e2) {
            throw new EncoderException(e2);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }
}
