package com.dh.im.tcp.server;

import com.dh.im.codec.MessageDecoder;
import com.dh.im.codec.MessageEncoder;
import com.dh.im.codec.config.BootstrapConfig;
import com.dh.im.tcp.handler.HeartBeatHandler;
import com.dh.im.tcp.handler.NettyServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

public class ImServer {
    private BootstrapConfig.TcpConfig config;
    private ServerBootstrap bootstrap;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public ImServer(BootstrapConfig.TcpConfig config) {

        this.config = config;

        bossGroup = new NioEventLoopGroup(config.getBossThreadSize());
        workerGroup = new NioEventLoopGroup(config.getWorkerThreadSize());

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
                        pipeline.addLast(new MessageDecoder());
                        pipeline.addLast(new MessageEncoder());
//                        pipeline.addLast(new IdleStateHandler(0, 0, 1));
                        pipeline.addLast(new HeartBeatHandler(config.getHeartBeatTime()));
                        pipeline.addLast(new NettyServerHandler(config.getBrokerId(), config.getLogicUrl()));
                    }
                });
    }

    public void start() {
        this.bootstrap.bind(this.config.getTcpPort());
    }


}
