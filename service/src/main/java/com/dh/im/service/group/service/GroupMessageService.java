package com.dh.im.service.group.service;

import com.dh.im.codec.park.message.ChatMessageAck;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.command.GroupEventCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.message.GroupChatMessageContent;
import com.dh.im.common.model.message.MessageContent;
import com.dh.im.common.model.message.OfflineMessageContent;
import com.dh.im.service.group.model.req.SendGroupMessageReq;
import com.dh.im.service.message.model.resp.SendMessageResp;
import com.dh.im.service.message.service.CheckSendMessageService;
import com.dh.im.service.message.service.MessageStoreService;
import com.dh.im.service.message.service.P2PMessageService;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.utils.MessageProducer;
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


@Service
@Slf4j
public class GroupMessageService {


    @Autowired
    private CheckSendMessageService checkSendMessageService;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private ImGroupMemberService imGroupMemberService;

    @Autowired
    private MessageStoreService messageStoreService;

    @Autowired
    private RedisSeq redisSeq;

    private final ThreadPoolExecutor threadPoolExecutor;

    {
        AtomicInteger num = new AtomicInteger(0);
        threadPoolExecutor = new ThreadPoolExecutor(8, 8, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    @Override
                    public Thread newThread(@NotNull Runnable r) {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true);
                        thread.setName("message-group-thread-" + num.getAndIncrement());
                        return thread;
                    }
                });
    }

    public void process(GroupChatMessageContent messageContent) {

        Integer appId = messageContent.getAppId();
        String messageId = messageContent.getMessageId();

        String key = messageContent.getAppId() + ":"
                + Constants.SeqConstants.GroupMessage + ":"
                + messageContent.getGroupId();

        GroupChatMessageContent messageFromMessageIdCache = messageStoreService
                .getMessageFromMessageIdCache(appId, messageId, GroupChatMessageContent.class);
        if (messageFromMessageIdCache != null) {
            threadPoolExecutor.execute(() -> {
                // 回 ACK 给自己
                ack(messageContent, ResponseVO.successResponse());
                // 发消息给同步在线端
                syncToSender(messageContent, messageContent);
                // 发消息给对方在线端
                dispatchMessage(messageContent);
            });
            return;
        }

        long seq = redisSeq.doGetSeq(key);
        messageContent.setMessageSequence(seq);

        threadPoolExecutor.execute(() -> {
            messageStoreService.storeGroupMessage(messageContent);

            List<String> groupMemberId =
                    imGroupMemberService.getGroupMemberId(messageContent.getGroupId(), messageContent.getAppId());
            messageContent.setMemberId(groupMemberId);

            OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
            BeanUtils.copyProperties(messageContent, offlineMessageContent);
            offlineMessageContent.setToId(messageContent.getGroupId());

            messageStoreService.storeGroupOfflineMessage(offlineMessageContent, groupMemberId);

            // 回 ACK 给自己
            ack(messageContent, ResponseVO.successResponse());
            // 发消息给同步在线端
            syncToSender(messageContent, messageContent);
            // 发消息给对方在线端
            dispatchMessage(messageContent);
            messageStoreService.setMessageFromMessageIdCache(appId, messageId, messageContent);
        });
    }

    private void dispatchMessage(GroupChatMessageContent messageContent) {

        messageContent.getMemberId().forEach(memberId -> {
            if (!memberId.equals(messageContent.getFromId())) {
                messageProducer.sendToUser(memberId,
                        GroupEventCommand.MSG_GROUP,
                        messageContent,
                        messageContent.getAppId());
            }
        });


    }

    private void ack(MessageContent messageContent, ResponseVO responseVO) {
        log.info("group msg ack, msgId = {}, checkResult：{}", messageContent.getMessageId(), responseVO.getCode());

        ChatMessageAck chatMessageAck = new ChatMessageAck(messageContent.getMessageId());
        responseVO.setData(chatMessageAck);
        // 发消息
        messageProducer.sendToUser(messageContent.getFromId(), GroupEventCommand.GROUP_MSG_ACK, responseVO, messageContent);

    }

    private void syncToSender(GroupChatMessageContent messageContent, ClientInfo clientInfo) {
        messageProducer.sendToUserExceptClient(messageContent.getFromId(),
                GroupEventCommand.MSG_GROUP, messageContent, messageContent);
    }

    public ResponseVO imServerPermissionCheck(String fromId, String groupId, Integer appId) {

        return checkSendMessageService.checkGroupMessage(fromId, groupId, appId);

    }

    public SendMessageResp send(SendGroupMessageReq req) {

        SendMessageResp sendMessageResp = new SendMessageResp();
        GroupChatMessageContent message = new GroupChatMessageContent();
        BeanUtils.copyProperties(req, message);

        messageStoreService.storeGroupMessage(message);

        sendMessageResp.setMessageKey(message.getMessageKey());
        sendMessageResp.setMessageTime(System.currentTimeMillis());
        //2.发消息给同步在线端
        syncToSender(message, message);
        //3.发消息给对方在线端
        dispatchMessage(message);

        return sendMessageResp;

    }
}
