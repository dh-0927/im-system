package com.dh.im.service.message.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.command.MessageCommand;
import com.dh.im.common.model.message.MessageContent;
import com.dh.im.common.model.message.MessageReadedContent;
import com.dh.im.common.model.message.MessageReciveAckContent;
import com.dh.im.service.message.service.MessageSyncService;
import com.dh.im.service.message.service.P2PMessageService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Slf4j
public class ChatOperatorReceiver {

    @Autowired
    private P2PMessageService p2PMessageService;

    @Autowired
    private MessageSyncService messageSyncService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.Im2MessageService, durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.Im2MessageService)
            )
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String, Object> headers,
                              Channel channel) throws IOException {

        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("chat meg from queue ：：{}", msg);
        Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
        try {

            JSONObject jsonObject = JSON.parseObject(msg);
            Integer command = jsonObject.getInteger("command");

            if (command.equals(MessageCommand.MSG_P2P.getCommand())) {
                // 处理消息
                MessageContent messageContent = jsonObject.toJavaObject(MessageContent.class);
                p2PMessageService.process(messageContent);
            } else if (command.equals(MessageCommand.MSG_RECIVE_ACK.getCommand())) {
                // 消息接收确认
                MessageReciveAckContent messageContent = jsonObject.toJavaObject(MessageReciveAckContent.class);
                messageSyncService.receiveMark(messageContent);
            }  else if (command.equals(MessageCommand.MSG_READED.getCommand())) {
                MessageReadedContent messageReadedContent = jsonObject.toJavaObject(MessageReadedContent.class);
                messageSyncService.readMark(messageReadedContent);
            }
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("处理消息出现异常：{}", e.getMessage());
            log.error("RMQ_CHAT_TRAN_ERROR", e);
            log.error("NACK_MSG:{}", msg);
            //第一个false 表示不批量拒绝，第二个false表示不重回队列
            channel.basicNack(deliveryTag, false, false);
        }

    }

}
