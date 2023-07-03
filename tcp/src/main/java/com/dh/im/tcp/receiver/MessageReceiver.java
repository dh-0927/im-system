package com.dh.im.tcp.receiver;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.proto.MessagePack;
import com.dh.im.common.constant.Constants;
import com.dh.im.tcp.receiver.process.BaseProcess;
import com.dh.im.tcp.receiver.process.ProcessFactory;
import com.dh.im.tcp.utils.MqFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Slf4j
public class MessageReceiver {

    private static String brokerId;

    private static void startReceiverMessage() {
        try {
            Channel channel =
                    MqFactory.getChannel(Constants.RabbitConstants.MessageService2Im + brokerId);

            channel.queueDeclare(Constants.RabbitConstants.MessageService2Im + brokerId,
                    true, false, false, null);

            channel.queueBind(Constants.RabbitConstants.MessageService2Im + brokerId,
                    Constants.RabbitConstants.MessageService2Im, brokerId);

            channel.basicConsume(Constants.RabbitConstants.MessageService2Im + brokerId, false,
                    new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            try {
                                String msgStr = new String(body);
                                MessagePack messagePack = JSONObject.parseObject(msgStr, MessagePack.class);
                                BaseProcess messageProcess = ProcessFactory.getMessageProcess(messagePack.getCommand());
                                messageProcess.process(messagePack);
                                log.info("收到的消息：{}", msgStr);
                                channel.basicAck(envelope.getDeliveryTag(), false);
                            } catch (IOException e) {
                                e.printStackTrace();
                                channel.basicNack(envelope.getDeliveryTag(), false, false);
                            }
                        }
                    });


        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init(String brokerId) {
        if (StringUtils.isBlank(MessageReceiver.brokerId)) {
            MessageReceiver.brokerId = brokerId;
        }
        startReceiverMessage();
    }
}
