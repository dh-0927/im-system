package com.dh.im.service.message.service;

import com.dh.im.codec.park.message.ChatMessageAck;
import com.dh.im.codec.park.message.MessageReciveServerAckPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ConversationTypeEnum;
import com.dh.im.common.enums.command.MessageCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.message.MessageContent;
import com.dh.im.common.model.message.OfflineMessageContent;
import com.dh.im.service.message.model.req.SendMessageReq;
import com.dh.im.service.message.model.resp.SendMessageResp;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.utils.ConversationIdGenerate;
import com.dh.im.service.utils.MessageProducer;
import com.mysql.cj.xdevapi.Client;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class P2PMessageService {

    @Autowired
    private CheckSendMessageService checkSendMessageService;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private MessageStoreService messageStoreService;

    @Autowired
    private RedisSeq redisSeq;

    private final ThreadPoolExecutor threadPoolExecutor;

    {
        final AtomicInteger num = new AtomicInteger(0);
        threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true);
                        thread.setName("message-process-thread-" + num.getAndIncrement());
                        return thread;
                    }
                });
    }

    public void process(MessageContent messageContent) {

        // 用messageId 从缓存中取
        Integer appId = messageContent.getAppId();
        String messageId = messageContent.getMessageId();
        MessageContent messageFromMessageIdCache = messageStoreService
                .getMessageFromMessageIdCache(appId, messageId, MessageContent.class);
        if (messageFromMessageIdCache != null) {
            threadPoolExecutor.execute(() -> {
                // **********往 MQ 丢************
                // 回 ACK 给自己
                ack(messageFromMessageIdCache, ResponseVO.successResponse());
                // 发消息给同步在线端
                syncToSender(messageFromMessageIdCache, messageFromMessageIdCache);
                // 发消息给对方在线端
                List<ClientInfo> clientInfos = dispatchMessage(messageFromMessageIdCache);
                if (clientInfos.isEmpty()) {
                    // 发送接收确认给发送方，要带上是服务端发送的标识
                    receiveAck(messageFromMessageIdCache);
                }
            });
            return;
        }

        String key = appId + ":"
                + Constants.SeqConstants.Message + ":"
                + ConversationIdGenerate.generateP2PId(messageContent.getFromId(), messageContent.getToId());
        long l = redisSeq.doGetSeq(key);
        messageContent.setMessageSequence(l);

        threadPoolExecutor.execute(() -> {
            // appId + Seq + (fromId + toId) / groupId

            // **********持久化************
            // 插入数据（异步执行）
            messageStoreService.storeP2PMessage(messageContent);

            OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
            BeanUtils.copyProperties(messageContent, offlineMessageContent);
            offlineMessageContent.setConversationType(ConversationTypeEnum.P2P.getCode());
            messageStoreService.storeOfflineMessage(offlineMessageContent);

            // **********往 MQ 丢************
            // 回 ACK 给自己
            ack(messageContent, ResponseVO.successResponse());
            // 发消息给同步在线端
            syncToSender(messageContent, messageContent);
            // 发消息给对方在线端
            List<ClientInfo> clientInfos = dispatchMessage(messageContent);
            // 将messageId存到缓存中
            messageStoreService.setMessageFromMessageIdCache(appId, messageId, messageContent);

            if (clientInfos.isEmpty()) {
                // 发送接收确认给发送方，要带上是服务端发送的标识
                receiveAck(messageContent);
            }
        });

    }

    private List<ClientInfo> dispatchMessage(MessageContent messageContent) {
        return messageProducer.sendToUser(messageContent.getToId(), MessageCommand.MSG_P2P,
                messageContent, messageContent.getAppId());
    }

    private void ack(MessageContent messageContent, ResponseVO responseVO) {
        log.info("msg ack, msgId = {}, checkResult：{}", messageContent.getMessageId(), responseVO.getCode());

        ChatMessageAck chatMessageAck = new ChatMessageAck(messageContent.getMessageId(), messageContent.getMessageSequence());
        responseVO.setData(chatMessageAck);
        // 发消息
        messageProducer.sendToUser(messageContent.getFromId(), MessageCommand.MSG_ACK, responseVO, messageContent);

    }

    public void receiveAck(MessageContent messageContent) {
        String toId = messageContent.getToId();
        Integer clientType = messageContent.getClientType();
        String imei = messageContent.getImei();
        String fromId = messageContent.getFromId();


        MessageReciveServerAckPack pack = new MessageReciveServerAckPack();
        pack.setFromId(toId);
        pack.setToId(fromId);
        pack.setMessageKey(messageContent.getMessageKey());
        pack.setMessageSequence(messageContent.getMessageSequence());
        pack.setServerSend(true);

        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setAppId(messageContent.getAppId());
        clientInfo.setClientType(clientType);
        clientInfo.setImei(imei);

        messageProducer.sendToUser(messageContent.getFromId(), MessageCommand.MSG_RECIVE_ACK, pack, clientInfo);
    }

    private void syncToSender(MessageContent messageContent, ClientInfo clientInfo) {
        messageProducer.sendToUserExceptClient(messageContent.getFromId(),
                MessageCommand.MSG_P2P, messageContent, messageContent);
    }

    public ResponseVO imServerPermissionCheck(String fromId, String toId, Integer appId) {

        ResponseVO checkFrom =
                checkSendMessageService.checkSenderDisabledAndMute(fromId, appId);
        if (!checkFrom.isOk()) {
            return checkFrom;
        }
        return checkSendMessageService.checkFriendShip(fromId, toId, appId);
    }


    public SendMessageResp send(SendMessageReq req) {

        SendMessageResp resp = new SendMessageResp();
        MessageContent messageContent = new MessageContent();
        BeanUtils.copyProperties(req, messageContent);

        // 插入数据
        messageStoreService.storeP2PMessage(messageContent);

        resp.setMessageKey(messageContent.getMessageKey());
        resp.setMessageTime(messageContent.getMessageTime());

        // 发消息给同步在线端
        syncToSender(messageContent, messageContent);
        // 发消息给对方在线端
        dispatchMessage(messageContent);

        return resp;
    }
}
