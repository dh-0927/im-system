package com.dh.im.service.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.dh.im.codec.park.conversation.DeleteConversationPack;
import com.dh.im.codec.park.conversation.UpdateConversationPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ConversationErrorCode;
import com.dh.im.common.enums.ConversationTypeEnum;
import com.dh.im.common.enums.command.ConversationEventCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.SyncReq;
import com.dh.im.common.model.SyncResp;
import com.dh.im.common.model.message.MessageReadedContent;
import com.dh.im.service.conversation.dao.ImConversationSetEntity;
import com.dh.im.service.conversation.dao.mapper.ImConversationSetMapper;
import com.dh.im.service.conversation.model.DeleteConversationReq;
import com.dh.im.service.conversation.model.UpdateConversationReq;
import com.dh.im.service.friendship.dao.ImFriendShipEntity;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.utils.MessageProducer;
import com.dh.im.service.utils.WriteUserSeq;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    @Autowired
    private ImConversationSetMapper imConversationSetMapper;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private RedisSeq redisSeq;

    @Autowired
    private WriteUserSeq writeUserSeq;

    public String genericConversationId(Integer type, String fromId, String toId) {
        return type + "_" + fromId + "_" + toId;
    }

    public void messageMarkRead(MessageReadedContent messageReadedContent) {

        String toId = messageReadedContent.getToId();
        if (messageReadedContent.getConversationType() == ConversationTypeEnum.GROUP.getCode()) {
            toId = messageReadedContent.getGroupId();
        }

        long messageSequence = messageReadedContent.getMessageSequence();
        Integer type = messageReadedContent.getConversationType();
        String fromId = messageReadedContent.getFromId();
        Integer appId = messageReadedContent.getAppId();
        String conversationId = genericConversationId(type, fromId, toId);

        LambdaQueryWrapper<ImConversationSetEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImConversationSetEntity::getConversationId, conversationId)
                .eq(ImConversationSetEntity::getAppId, appId);

        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(lqw);
        if (imConversationSetEntity == null) {
            long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Conversation);
            imConversationSetEntity = new ImConversationSetEntity();
            imConversationSetEntity.setConversationId(conversationId);
            BeanUtils.copyProperties(messageReadedContent, imConversationSetEntity);
            imConversationSetEntity.setToId(toId);
            imConversationSetEntity.setReadedSequence(messageSequence);
            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.insert(imConversationSetEntity);
            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Conversation, seq);
        } else {
            long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Conversation);
            imConversationSetEntity.setReadedSequence(messageSequence);
            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.readMark(imConversationSetEntity);
            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Conversation, seq);

        }
    }


    public ResponseVO deleteConversation(DeleteConversationReq req) {
        String conversationId = req.getConversationId();
        String fromId = req.getFromId();
        Integer appId = req.getAppId();
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setClientType(req.getClientType());
        clientInfo.setAppId(appId);
        clientInfo.setImei(req.getImei());

        /**
         * 是否将置顶信息和免打扰模式关闭
         */
//        LambdaQueryWrapper<ImConversationSetEntity> lqw = new LambdaQueryWrapper<>();
//        lqw.eq(ImConversationSetEntity::getConversationId, conversationId)
//                .eq(ImConversationSetEntity::getAppId, appId);
//        ImConversationSetEntity entity = imConversationSetMapper.selectOne(lqw);
//        if (entity != null) {
//            entity.setIsMute(0);
//            entity.setIsTop(0);
//            imConversationSetMapper.update(entity, lqw);
//        }


        if (appConfig.getDeleteConversationSyncMode() == 1) {   // 是否发送给同步端
            DeleteConversationPack pack = new DeleteConversationPack();
            pack.setConversationId(conversationId);
            messageProducer.sendToUserExceptClient(
                    fromId,
                    ConversationEventCommand.CONVERSATION_DELETE,
                    pack,
                    clientInfo);
        }
        return ResponseVO.successResponse();
    }

    public ResponseVO updateConversation(UpdateConversationReq req) {

        Integer isMute = req.getIsMute();
        Integer isTop = req.getIsTop();
        if (isTop == null && isMute == null) {
            // 返回失败
            return ResponseVO.errorResponse(ConversationErrorCode.CONVERSATION_UPDATE_PARAM_ERROR);
        }

        String conversationId = req.getConversationId();
        Integer appId = req.getAppId();
        String fromId = req.getFromId();
        Integer clientType = req.getClientType();
        String imei = req.getImei();
        ClientInfo clientInfo = new ClientInfo(appId, clientType, imei);

        LambdaQueryWrapper<ImConversationSetEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImConversationSetEntity::getConversationId, conversationId)
                .eq(ImConversationSetEntity::getAppId, appId);
        ImConversationSetEntity entity = imConversationSetMapper.selectOne(lqw);
        long seq = redisSeq.doGetSeq(appId + ":" + Constants.SeqConstants.Conversation);
        if (entity != null) {
            if (isMute != null) {
                entity.setIsMute(isMute);
            }
            if (isTop != null) {
                entity.setIsTop(isTop);
            }
            entity.setSequence(seq);
            imConversationSetMapper.update(entity, lqw);

            writeUserSeq.writeUserSeq(appId, fromId, Constants.SeqConstants.Conversation, seq);
        }

        UpdateConversationPack pack = new UpdateConversationPack();
        pack.setConversationId(conversationId);
        pack.setIsMute(isMute);
        pack.setSequence(seq);
        pack.setIsTop(isTop);
        pack.setConversationType(entity.getConversationType());
        messageProducer.sendToUserExceptClient(
                fromId,
                ConversationEventCommand.CONVERSATION_UPDATE,
                pack,
                clientInfo);

        return ResponseVO.successResponse();
    }

    public ResponseVO syncConversationSet(SyncReq req) {
        if (req.getMaxLimit() > 100) {
            req.setMaxLimit(100);
        }

        SyncResp<ImConversationSetEntity> resp = new SyncResp<>();
        LambdaQueryWrapper<ImConversationSetEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImConversationSetEntity::getFromId, req.getOperator())
                .eq(ImConversationSetEntity::getAppId, req.getAppId())
                .gt(ImConversationSetEntity::getSequence, req.getLastSequence())
                .last("limit " + req.getMaxLimit())
                .orderByAsc(ImConversationSetEntity::getSequence);

        List<ImConversationSetEntity> list = imConversationSetMapper.selectList(lqw);

        if (CollectionUtils.isNotEmpty(list)) {
            ImConversationSetEntity maxSeqEntity = list.get(list.size() - 1);
            resp.setDataList(list);
            Long maxSeq = imConversationSetMapper.geConversationSetMaxSeq(req.getAppId(), req.getOperator());
            resp.setMaxSequence(maxSeq);
            resp.setCompleted(maxSeqEntity.getSequence() >=  maxSeq);
            return ResponseVO.successResponse(resp);
        }
        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);
    }
}
