package com.dh.message.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dh.im.common.constant.Constants;
import com.dh.message.dao.ImMessageBodyEntity;
import com.dh.message.model.DoStoreP2PMessageDto;
import com.dh.message.service.StoreMessageService;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class StoreP2PMessageReceiver {

    @Autowired
    private StoreMessageService storeMessageService;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = Constants.RabbitConstants.StoreP2PMessage, durable = "true"),
                    exchange = @Exchange(value = Constants.RabbitConstants.StoreP2PMessage)
            ), concurrency = "1"
    )
    public void onChatMessage(@Payload Message message,
                              @Headers Map<String, Object> headers,
                              Channel channel) throws IOException {

        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("store meg from queue ：：：{}", msg);
        Long deliveryTag = (Long) headers.get(AmqpHeaders.DELIVERY_TAG);
        try {
            JSONObject jsonObject = JSON.parseObject(msg);

            DoStoreP2PMessageDto dto = jsonObject.toJavaObject(DoStoreP2PMessageDto.class);
            ImMessageBodyEntity messageBody = jsonObject.getObject("messageBody", ImMessageBodyEntity.class);
            dto.setImMessageBodyEntity(messageBody);

            storeMessageService.doStoreP2PMessage(dto);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("持久化消息出现异常：{}", e.getMessage());
            log.error("RMQ_STORE_TRAN_ERROR", e);
            log.error("NACK_MSG:{}", msg);
            //第一个false 表示不批量拒绝，第二个false表示不重回队列
            channel.basicNack(deliveryTag, false, false);
        }

    }
}
