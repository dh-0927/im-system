package com.dh.im.service.message.service;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ConversationTypeEnum;
import com.dh.im.common.enums.DelFlagEnum;
import com.dh.im.common.model.message.*;
import com.dh.im.service.conversation.service.ConversationService;
import com.dh.im.service.group.dao.ImGroupMessageHistoryEntity;
import com.dh.im.service.group.dao.mapper.ImGroupMessageHistoryMapper;
import com.dh.im.service.message.dao.ImMessageBodyEntity;
import com.dh.im.service.message.dao.ImMessageHistoryEntity;
import com.dh.im.service.message.dao.mapper.ImMessageBodyMapper;
import com.dh.im.service.message.dao.mapper.ImMessageHistoryMapper;
import com.dh.im.service.utils.SnowflakeIdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class MessageStoreService {

    @Autowired
    private ImMessageHistoryMapper imMessageHistoryMapper;

    @Autowired
    private ImMessageBodyMapper imMessageBodyMapper;

    @Autowired
    private SnowflakeIdWorker snowflakeIdWorker;

    @Autowired
    private ImGroupMessageHistoryMapper imGroupMessageHistoryMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AppConfig appConfig;

    // 写扩散
    @Transactional
    public void storeP2PMessage(MessageContent messageContent) {
        // messageContent 转化成 messageBody
//        ImMessageBody messageBody = extractMessageBody(messageContent);
        // 插入 messageBody
//        imMessageBodyMapper.insert(messageBody);
//        // 转化成 MessageHistory
//        List<ImMessageHistoryEntity> imMessageHistoryEntities = extractToP2PMessageHistory(messageContent, messageBody);
//        // 批量插入
//        imMessageHistoryMapper.insertBatchSomeColumn(imMessageHistoryEntities);
//
//        messageContent.setMessageKey(messageBody.getMessageKey());

        ImMessageBody messageBody = extractMessageBody(messageContent);

        DoStoreP2PMessageDto dto = new DoStoreP2PMessageDto();
        dto.setMessageContent(messageContent);
        dto.setMessageBody(messageBody);
        messageContent.setMessageKey(messageBody.getMessageKey());

        // 发送 mq 消息
        rabbitTemplate.convertAndSend(Constants.RabbitConstants.StoreP2PMessage, "", JSONObject.toJSONString(dto));

    }

    private List<ImMessageHistoryEntity> extractToP2PMessageHistory(MessageContent messageContent,
                                                                    ImMessageBodyEntity imMessageBodyEntity) {
        List<ImMessageHistoryEntity> list = new ArrayList<>();
        ImMessageHistoryEntity fromHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent, fromHistory);
        fromHistory.setOwnerId(messageContent.getFromId());
        fromHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        fromHistory.setCreateTime(System.currentTimeMillis());

        ImMessageHistoryEntity toHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent, toHistory);
        toHistory.setOwnerId(messageContent.getToId());
        toHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        toHistory.setCreateTime(System.currentTimeMillis());

        list.add(fromHistory);
        list.add(toHistory);
        return list;
    }

    private ImMessageBody extractMessageBody(MessageContent messageContent) {
        ImMessageBody messageBody = new ImMessageBody();
        messageBody.setAppId(messageContent.getAppId());
        messageBody.setMessageKey(snowflakeIdWorker.nextId());
        messageBody.setCreateTime(System.currentTimeMillis());
        messageBody.setSecurityKey("");
        messageBody.setExtra(messageContent.getExtra());
        messageBody.setDelFlag(DelFlagEnum.NORMAL.getCode());
        messageBody.setMessageTime(messageContent.getMessageTime());
        messageBody.setMessageBody(messageContent.getMessageBody());
        return messageBody;
    }

    @Transactional
    public void storeGroupMessage(GroupChatMessageContent messageContent) {

        ImMessageBody messageBody = extractMessageBody(messageContent);
        DoStoreGroupMessageDto dto = new DoStoreGroupMessageDto();
        dto.setMessageBody(messageBody);
        dto.setGroupChatMessageContent(messageContent);

        // 发送 mq 消息
        rabbitTemplate.convertAndSend(Constants.RabbitConstants.StoreGroupMessage, "", JSONObject.toJSONString(dto));
        messageContent.setMessageKey(messageBody.getMessageKey());
    }

    private ImGroupMessageHistoryEntity extractToGroupMessageHistory
            (GroupChatMessageContent messageContent, ImMessageBodyEntity messageBodyEntity) {
        ImGroupMessageHistoryEntity result = new ImGroupMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent, result);
        result.setGroupId(messageContent.getGroupId());
        result.setMessageKey(messageBodyEntity.getMessageKey());
        result.setCreateTime(System.currentTimeMillis());
        return result;
    }

    public <T extends MessageContent> void setMessageFromMessageIdCache(Integer appId, String messageId, T t) {
        // appId : cache : messageId
        String key = appId + ":"
                + Constants.RedisConstants.cacheMessage + ":"
                + messageId;
        stringRedisTemplate.opsForValue().set(
                key,
                JSONObject.toJSONString(t),
                300, TimeUnit.SECONDS);
    }

    public <T extends MessageContent> T getMessageFromMessageIdCache(Integer appId, String messageId, Class<T> clazz) {
        // appId : cache : messageId
        String key = appId + ":"
                + Constants.RedisConstants.cacheMessage + ":"
                + messageId;

        String msg = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isBlank(msg)) {
            return null;
        }
        return JSONObject.parseObject(msg, clazz);
    }

    // 存储单聊离线消息
    public void storeOfflineMessage(OfflineMessageContent offlineMessageContent) {

        Integer appId = offlineMessageContent.getAppId();
        String fromId = offlineMessageContent.getFromId();
        String toId = offlineMessageContent.getToId();

        // 判断队列中的数据是否超过设定值
        String fromKey = appId + ":" + Constants.RedisConstants.OfflineMessage + ":" + fromId;
        String toKey = appId + ":" + Constants.RedisConstants.OfflineMessage + ":" + toId;
        ZSetOperations<String, String> zSet = stringRedisTemplate.opsForZSet();

        if (Objects.requireNonNull(zSet.zCard(fromKey)).intValue() > appConfig.getOfflineMessageCount()) {
            zSet.removeRange(fromKey, 0, 0);
        }
        offlineMessageContent.setConversationId(
                conversationService.genericConversationId(
                        ConversationTypeEnum.P2P.getCode(),
                        fromId,
                        toId));
        zSet.add(fromKey,
                JSONObject.toJSONString(offlineMessageContent),
                offlineMessageContent.getMessageKey());

        if (Objects.requireNonNull(zSet.zCard(toKey)).intValue() > appConfig.getOfflineMessageCount()) {
            zSet.removeRange(toKey, 0, 0);
        }
        offlineMessageContent.setConversationId(
                conversationService.genericConversationId(
                        ConversationTypeEnum.P2P.getCode(),
                        toId,
                        fromId));
        zSet.add(toKey,
                JSONObject.toJSONString(offlineMessageContent),
                offlineMessageContent.getMessageKey());
    }

    public void storeGroupOfflineMessage(OfflineMessageContent offlineMessage, List<String> memberIds) {

        ZSetOperations<String, String> operations = stringRedisTemplate.opsForZSet();
        offlineMessage.setConversationType(ConversationTypeEnum.GROUP.getCode());

        for (String memberId : memberIds) {
            // 找到toId的队列
            String toKey = offlineMessage.getAppId() + ":" +
                    Constants.RedisConstants.OfflineMessage + ":" +
                    memberId;
            offlineMessage.setConversationId(conversationService.genericConversationId(
                    ConversationTypeEnum.GROUP.getCode(), memberId, offlineMessage.getToId()
            ));
            //判断 队列中的数据是否超过设定值
            if (Objects.requireNonNull(operations.zCard(toKey)) > appConfig.getOfflineMessageCount()) {
                operations.removeRange(toKey, 0, 0);
            }
            // 插入 数据 根据messageKey 作为分值
            operations.add(toKey, JSONObject.toJSONString(offlineMessage),
                    offlineMessage.getMessageKey());
        }


    }

}
