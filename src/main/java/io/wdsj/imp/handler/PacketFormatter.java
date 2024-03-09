package io.wdsj.imp.handler;

import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PacketFormatter extends MessageToByteEncoder<ByteBuf> {
    public int getVarIntSize(int len) {
        for (int i = 1; i < 5; i++) {
            if ((len & -1 << i * 7) == 0)
                return i;
        }
        return 5;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int length = msg.readableBytes();
        int varIntSize = getVarIntSize(length);
        if (varIntSize > 3) {
            throw new IllegalStateException("Something went wrong in the Packet Formatter!");
        }
        out.ensureWritable(varIntSize + length);
        ByteBufHelper.writeVarInt(out, length);
        out.writeBytes(msg, msg.readerIndex(), length);
    }
}
