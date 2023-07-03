package com.dh.im.codec;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.proto.Message;
import com.dh.im.codec.proto.MessageHeader;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class MessageDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        /**
         * 请求头：（
         * 指令
         * 版本
         * clientType
         * 消息解析类型
         * appID
         * IMEL(国际移动设备识别码)
         * bodyLen
         * IMEL号
         * ）
         * 请求体
         */
        if (in.readableBytes() < 28) {
            return;
        }

        int command = in.readInt();
        int version = in.readInt();
        int clientType = in.readInt();
        int messageType = in.readInt();
        int appId = in.readInt();
        int imeiLen = in.readInt();
        int bodyLen = in.readInt();

        if (in.readableBytes() < bodyLen + imeiLen) {
            in.resetReaderIndex();
            return;
        }

        // 获取iMei
        byte[] imeiData = new byte[imeiLen];
        in.readBytes(imeiData);
        String iMei = new String(imeiData);

        // 获取body
        byte[] bodyData = new byte[bodyLen];
        in.readBytes(bodyData);

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setCommand(command);
        messageHeader.setVersion(version);
        messageHeader.setClientType(clientType);
        messageHeader.setMessageType(messageType);
        messageHeader.setAppId(appId);
        messageHeader.setImeiLen(imeiLen);
        messageHeader.setBodyLen(bodyLen);
        messageHeader.setImei(iMei);

        Message message = new Message();
        message.setMessageHeader(messageHeader);

        // 通过 messageType 解析body
        if (messageType == 0x0) {
            String body = new String(bodyData);
            JSONObject parse = (JSONObject) JSONObject.parse(body);
            message.setMessagePack(parse);
        }

        in.markReaderIndex();
        out.add(message);

    }

}
