package com.zguishen.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetSocketAddress;

/**
 * 参考：
 * https://github.com/waylau/netty-4-user-guide-demos 
 * https://houbb.github.io/2017/11/16/netty-16-websocket-02-websocket-demo-02
 */
public class WsServer {

    public static void main(String[] args) {
        final int port = 8080;
        // NIO服务启动类
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        
        // 处理I/O操作的多线程时间循环器。boss接收进来的连接
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        // worker处理已经被接收的连接。boss接收到连接就会把信息注册到worker上
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class) // nio channel接收进来的连接
                .childHandler(new WsServerInitializer()); // 配置handle

        ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(port)).syncUninterruptibly();
        if(!channelFuture.isSuccess()) {
            channelFuture.cause().printStackTrace();
        } else {
            System.out.println("Server listen on port: " + port);
        }

        channelFuture.channel().closeFuture().syncUninterruptibly();
        //close group
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}


class WsServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        ChannelPipeline pipeline = socketChannel.pipeline();

        // webSocket 是 http 协议的升级
        pipeline.addLast(new HttpServerCodec())
                //避免大文件写入 oom
                .addLast(new ChunkedWriteHandler())
                //使用对象聚合
                .addLast(new HttpObjectAggregator(64*1024));

        //ws://server:port/context_path
        //参数指的是contex_path
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"))
                .addLast(new HttpRequestHandler("/ws")) // 处理HTTP的handle
                .addLast(new TextWebSocketFrameHandler()); // 自定义的文本处理handler
    }
}


class TextWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    public static ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        //读取客户端的内容
        Channel channel = ctx.channel();
        System.out.println(channel.remoteAddress()+": " + msg.text());

        //遍历所有连接channel，服务端反馈内容给所有channel
        for (Channel ch : channels) {
            if (ch != channel) {
                ch.writeAndFlush(new TextWebSocketFrame("[" + channel.remoteAddress() + "]" + msg.text()));
            } else
                channel.writeAndFlush(new TextWebSocketFrame("[我]" + msg.text() ));
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        System.out.println("[客户端加入] " + channel.remoteAddress());
        // 发送消息到所有客户端
        channels.writeAndFlush("[客户端加入] " + channel.remoteAddress());
        // 新加入连接放到集合
        channels.add(channel);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        System.out.println("[客户端离开] " + channel.remoteAddress());
        // 发送消息到所有客户端
        channels.writeAndFlush("[客户端离开] " + channel.remoteAddress());
        // 离开的客户端会自动从ChannelGroup移除，这里不需要手动remove 
        // channels.remove(channel);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception { // (5)
        Channel incoming = ctx.channel();
        System.out.println("[客户端]:"+incoming.remoteAddress()+"在线");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception { // (6)
        Channel incoming = ctx.channel();
        System.out.println("[客户端]:"+incoming.remoteAddress()+"掉线");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel incoming = ctx.channel();
        System.out.println("[客户端]:"+incoming.remoteAddress()+"异常");
        // 当出现异常就关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}