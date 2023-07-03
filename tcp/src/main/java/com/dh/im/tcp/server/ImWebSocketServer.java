package com.dh.im.tcp.server;

import com.dh.im.codec.WebSocketMessageDecoder;
import com.dh.im.codec.WebSocketMessageEncoder;
import com.dh.im.codec.config.BootstrapConfig;
import com.dh.im.tcp.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

public class ImWebSocketServer {

    private BootstrapConfig.TcpConfig config;
    private ServerBootstrap bootstrap;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public ImWebSocketServer(BootstrapConfig.TcpConfig config) {

        this.config = config;

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 10240)    // 服务端可连接队列大小
                .option(ChannelOption.SO_REUSEADDR, true)   // 表示语序重复使用本地地址和端口
//                    .option(ChannelOption.TCP_NODELAY, true)    // 是否禁用Nagle算法（能否批量发送数据），开启的话会可以减小网络开销，但是影响消息实时性
//                    .option(ChannelOption.SO_KEEPALIVE, true)   // 保活开关2h没有数据服务端会发送心跳
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // websocket 基于http协议，所以要有http编解码器
                        pipeline.addLast("http-codec", new HttpServerCodec());
                        // 对写大数据流的支持
                        pipeline.addLast("http-chunked", new ChunkedWriteHandler());
                        // 几乎在netty中的编程，都会使用到此hanler
                        pipeline.addLast("aggregator", new HttpObjectAggregator(65535));
                        /**
                         * websocket 服务器处理的协议，用于指定给客户端连接访问的路由 : /ws
                         * 本handler会帮你处理一些繁重的复杂的事
                         * 会帮你处理握手动作： handshaking（close, ping, pong） ping + pong = 心跳
                         * 对于websocket来讲，都是以frames进行传输的，不同的数据类型对应的frames也不同
                         */
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
                            pipeline.addLast(new WebSocketMessageDecoder());
                            pipeline.addLast(new WebSocketMessageEncoder());
                        pipeline.addLast(new NettyServerHandler(config.getBrokerId(), config.getLogicUrl()));
                    }
                });
    }

    public void start() {
        this.bootstrap.bind(this.config.getWebSocketPort());
    }


}
