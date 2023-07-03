package com.dh.im.service.group.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.command.GroupEventCommand;
import com.dh.im.common.model.message.GroupChatMessageContent;
import com.dh.im.common.model.message.MessageReadedContent;
import com.dh.im.service.group.service.GroupMessageService;
import com.dh.im.service.message.service.MessageSyncService;
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
public class GroupChatOperateReceiver {

    @Autowired
    private GroupMessageService groupMessageService;

    @Autowired
    private MessageSyncService messageSyncService;


    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.Im2GroupService, durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.Im2GroupService)
            ), concurrency = "1"
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String, Object> headers,
                              Channel channel) throws IOException {

        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("chat meg from queue ：：：{}", msg);
        Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
        try {

            JSONObject jsonObject = JSON.parseObject(msg);
            Integer command = jsonObject.getInteger("command");

            if (command.equals(GroupEventCommand.MSG_GROUP.getCommand())) {
                // 处理消息
                GroupChatMessageContent messageContent = jsonObject.toJavaObject(GroupChatMessageContent.class);
                groupMessageService.process(messageContent);
            } else if (command.equals(GroupEventCommand.MSG_GROUP_READED.getCommand())) {
                MessageReadedContent messageReaded = JSONObject.parseObject(msg, MessageReadedContent.class);
                messageSyncService.groupReadMark(messageReaded);
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
