package com.zguishen.discard;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Date;

public class TimeClientHandler extends ChannelInboundHandlerAdapter {

    ByteBuf bf;
    
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
       bf = ctx.alloc().buffer(4);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        bf.release();
        bf = null;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf m = (ByteBuf) msg;
        bf.writeBytes(m);
        m.release();
        try {
            long currentTimeMillis = (bf.readUnsignedInt() - 2208988800L) * 1000L;
            System.out.println(new Date(currentTimeMillis));
            Thread.sleep(1000L);
        } finally {
            ctx.close();
            //m.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
       cause.printStackTrace();
       ctx.close();
    }
}
