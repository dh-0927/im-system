package com.dh.im.tcp.receiver.process;

import com.dh.im.codec.proto.MessagePack;
import com.dh.im.tcp.utils.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;

public abstract class BaseProcess {

    public abstract void processBefore();

    public void process(MessagePack messagePack) {
        NioSocketChannel channel = SessionSocketHolder.get(messagePack.getAppId(), messagePack.getClientType(),
                messagePack.getImei(), messagePack.getUserId());

        if (channel != null) {
            channel.writeAndFlush(messagePack);
        }
    }

    public abstract void processAfter();

}
