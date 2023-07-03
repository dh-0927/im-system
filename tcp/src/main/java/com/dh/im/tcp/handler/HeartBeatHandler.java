package com.dh.im.tcp.handler;

import com.dh.im.common.constant.Constants;
import com.dh.im.tcp.utils.SessionSocketHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeartBeatHandler extends ChannelInboundHandlerAdapter {

    private Long heartBeatTime;

    public HeartBeatHandler(Long heartBeatTime) {
        this.heartBeatTime = heartBeatTime;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        // 判断 evt 是否是 IdleStateEven (用于触发用户事件，包含 读空闲/写空闲/读写空闲)
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.info("进入读空闲");
            } else if (event.state() == IdleState.WRITER_IDLE) {
                log.info("进入写空闲");
            } else if (event.state() == IdleState.ALL_IDLE) {
                Long lastReadTime = (Long) ctx.channel().attr(AttributeKey.valueOf(Constants.ReadTime)).get();
                long nowTime = System.currentTimeMillis();

                if (lastReadTime != null && nowTime - lastReadTime > heartBeatTime) {
                    // 离线
                    SessionSocketHolder.offlineUserSession(((NioSocketChannel) ctx.channel()));
                }
            }
        }
    }

}
