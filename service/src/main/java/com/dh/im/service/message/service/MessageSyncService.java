package com.dh.im.service.message.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.dh.im.codec.park.message.MessageReadedPack;
import com.dh.im.codec.park.message.RecallMessageNotifyPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ConversationTypeEnum;
import com.dh.im.common.enums.DelFlagEnum;
import com.dh.im.common.enums.MessageErrorCode;
import com.dh.im.common.enums.command.Command;
import com.dh.im.common.enums.command.GroupEventCommand;
import com.dh.im.common.enums.command.MessageCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.SyncReq;
import com.dh.im.common.model.SyncResp;
import com.dh.im.common.model.message.*;
import com.dh.im.service.conversation.service.ConversationService;
import com.dh.im.service.group.service.ImGroupMemberService;
import com.dh.im.service.message.dao.ImMessageBodyEntity;
import com.dh.im.service.message.dao.mapper.ImMessageBodyMapper;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.utils.ConversationIdGenerate;
import com.dh.im.service.utils.GroupMessageProducer;
import com.dh.im.service.utils.MessageProducer;
import com.dh.im.service.utils.SnowflakeIdWorker;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MessageSyncService {

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ImMessageBodyMapper imMessageBodyMapper;

    @Autowired
    private GroupMessageProducer groupMessageProducer;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ImGroupMemberService imGroupMemberService;

    @Autowired
    private RedisSeq redisSeq;

    public void receiveMark(MessageReciveAckContent messageReciveAckContent) {
        messageProducer.sendToUser(messageReciveAckContent.getToId(), MessageCommand.MSG_RECIVE_ACK,
                messageReciveAckContent, messageReciveAckContent.getAppId());
    }


    // 消息已读
    // 更新会话的seq，通知在线的同步端发送指定的command，发送已读回执
    public void readMark(MessageReadedContent messageReadedContent) {
        conversationService.messageMarkRead(messageReadedContent);

        MessageReadedPack messageReadedPack = new MessageReadedPack();
        BeanUtils.copyProperties(messageReadedContent, messageReadedPack);

        syncToSender(messageReadedPack, messageReadedContent, MessageCommand.MSG_READED_NOTIFY);

        // 发送给对方
        messageProducer.sendToUser(
                messageReadedContent.getToId(),
                MessageCommand.MSG_READED_RECEIPT,
                messageReadedPack,
                messageReadedContent.getAppId());

    }

    // 发送自己的同步端
    private void syncToSender(MessageReadedPack messageReadedPack, MessageReadedContent messageReadedContent, Command command) {

        messageProducer.sendToUserExceptClient(
                messageReadedPack.getFromId(),
                command,
                messageReadedPack,
                messageReadedContent);
    }

    public void groupReadMark(MessageReadedContent messageReaded) {
        conversationService.messageMarkRead(messageReaded);
        MessageReadedPack messageReadedPack = new MessageReadedPack();
        BeanUtils.copyProperties(messageReaded, messageReadedPack);
        syncToSender(messageReadedPack, messageReaded, GroupEventCommand.MSG_GROUP_READED_NOTIFY);

        messageProducer.sendToUser(messageReadedPack.getToId(), GroupEventCommand.MSG_GROUP_READED_RECEIPT,
                messageReaded, messageReaded.getAppId());
    }

    public ResponseVO syncOfflineMessage(SyncReq req) {

        SyncResp<OfflineMessageContent> resp = new SyncResp<>();
        Integer appId = req.getAppId();
        String fromId = req.getOperator();

        String key = appId + ":" + Constants.RedisConstants.OfflineMessage + ":" + fromId;
        long maxSeq = 0L;
        ZSetOperations<String, String> setOperations = stringRedisTemplate.opsForZSet();
        Set<ZSetOperations.TypedTuple<String>> set = setOperations.reverseRangeWithScores(key, 0, 0);
        if (CollectionUtils.isNotEmpty(set)) {
            ArrayList<ZSetOperations.TypedTuple<String>> list = new ArrayList<>(set);
            ZSetOperations.TypedTuple<String> tuple = list.get(0);
            maxSeq = Objects.requireNonNull(tuple.getScore()).longValue();
        }

        resp.setMaxSequence(maxSeq);

        List<OfflineMessageContent> respList = Objects.requireNonNull(
                        setOperations.rangeByScoreWithScores(
                                key,
                                req.getLastSequence(),
                                maxSeq,
                                0,
                                req.getMaxLimit()))
                .stream()
                .map(tuple -> {
                    String value = tuple.getValue();
                    return JSONObject.parseObject(value, OfflineMessageContent.class);
                }).collect(Collectors.toList());

        resp.setDataList(respList);
        if (CollectionUtils.isNotEmpty(respList)) {
            OfflineMessageContent offMsg = respList.get(respList.size() - 1);
            resp.setCompleted(maxSeq <= offMsg.getMessageKey());
        }

        return ResponseVO.successResponse(resp);
    }

    /**
     * 修改历史消息的状态
     * 修改离线消息的状态
     * ack给发送方
     * 发送给同步端
     * 发送给消息的接收方
     */
    public void recallMessage(RecallMessageContent content) {

        Long messageTime = content.getMessageTime();
        Long now = System.currentTimeMillis();

        RecallMessageNotifyPack pack = new RecallMessageNotifyPack();
        BeanUtils.copyProperties(content,pack);

        if(120000L < now - messageTime){
            recallAck(pack,ResponseVO.errorResponse(MessageErrorCode.MESSAGE_RECALL_TIME_OUT),content);
            return;
        }

        QueryWrapper<ImMessageBodyEntity> query = new QueryWrapper<>();
        query.eq("app_id",content.getAppId());
        query.eq("message_key",content.getMessageKey());
        ImMessageBodyEntity body = imMessageBodyMapper.selectOne(query);

        if(body == null){
            recallAck(pack,ResponseVO.errorResponse(MessageErrorCode.MESSAGEBODY_IS_NOT_EXIST),content);
            return;
        }

        if(body.getDelFlag() == DelFlagEnum.DELETE.getCode()){
            recallAck(pack,ResponseVO.errorResponse(MessageErrorCode.MESSAGE_IS_RECALLED),content);

            return;
        }

        body.setDelFlag(DelFlagEnum.DELETE.getCode());
        imMessageBodyMapper.update(body,query);

        String toId = content.getToId();
        String fromId = content.getFromId();
        if(content.getConversationType() == ConversationTypeEnum.P2P.getCode()){

            // 找到fromId的队列
            String fromKey = content.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + fromId;
            // 找到toId的队列
            String toKey = content.getAppId() + ":" + Constants.RedisConstants.OfflineMessage + ":" + toId;

            OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
            BeanUtils.copyProperties(content,offlineMessageContent);
            offlineMessageContent.setDelFlag(DelFlagEnum.DELETE.getCode());
            offlineMessageContent.setMessageKey(content.getMessageKey());
            offlineMessageContent.setConversationType(ConversationTypeEnum.P2P.getCode());
            offlineMessageContent.setConversationId(conversationService.genericConversationId(offlineMessageContent.getConversationType()
                    , fromId, toId));
            offlineMessageContent.setMessageBody(body.getMessageBody());

            long seq = redisSeq.doGetSeq(content.getAppId() + ":" + Constants.SeqConstants.Message + ":" + ConversationIdGenerate.generateP2PId(fromId, toId));
            offlineMessageContent.setMessageSequence(seq);

            long messageKey = SnowflakeIdWorker.nextId();

            redisTemplate.opsForZSet().add(fromKey,JSONObject.toJSONString(offlineMessageContent),messageKey);
            redisTemplate.opsForZSet().add(toKey,JSONObject.toJSONString(offlineMessageContent),messageKey);

            //ack
            recallAck(pack,ResponseVO.successResponse(),content);
            //分发给同步端
            messageProducer.sendToUserExceptClient(fromId,
                    MessageCommand.MSG_RECALL_NOTIFY,pack,content);
            //分发给对方
            messageProducer.sendToUser(toId,MessageCommand.MSG_RECALL_NOTIFY,
                    pack,content.getAppId());
        }else{
            List<String> groupMemberId = imGroupMemberService.getGroupMemberId(toId, content.getAppId());
            long seq = redisSeq.doGetSeq(content.getAppId() + ":" + Constants.SeqConstants.Message + ":" + ConversationIdGenerate.generateP2PId(fromId, toId));
            //ack
            recallAck(pack,ResponseVO.successResponse(),content);
            //发送给同步端
            messageProducer.sendToUserExceptClient(fromId, MessageCommand.MSG_RECALL_NOTIFY, pack
                    , content);
            for (String memberId : groupMemberId) {
                String toKey = content.getAppId() + ":" + Constants.SeqConstants.Message + ":" + memberId;
                OfflineMessageContent offlineMessageContent = new OfflineMessageContent();
                offlineMessageContent.setDelFlag(DelFlagEnum.DELETE.getCode());
                BeanUtils.copyProperties(content,offlineMessageContent);
                offlineMessageContent.setConversationType(ConversationTypeEnum.GROUP.getCode());
                offlineMessageContent.setConversationId(conversationService.genericConversationId(offlineMessageContent.getConversationType()
                        , fromId, toId));
                offlineMessageContent.setMessageBody(body.getMessageBody());
                offlineMessageContent.setMessageSequence(seq);
                redisTemplate.opsForZSet().add(toKey,JSONObject.toJSONString(offlineMessageContent),seq);

                groupMessageProducer.producer(fromId, MessageCommand.MSG_RECALL_NOTIFY, pack,content);
            }
        }

    }
    private void recallAck(RecallMessageNotifyPack recallPack, ResponseVO<Object> success, ClientInfo clientInfo) {
        messageProducer.sendToUser(recallPack.getFromId(),
                MessageCommand.MSG_RECALL_ACK, success, clientInfo);
    }


}
