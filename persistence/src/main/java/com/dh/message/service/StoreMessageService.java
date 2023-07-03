package com.dh.message.service;

import com.dh.im.common.model.message.GroupChatMessageContent;
import com.dh.im.common.model.message.MessageContent;
import com.dh.message.dao.ImGroupMessageHistoryEntity;
import com.dh.message.dao.ImMessageBodyEntity;
import com.dh.message.dao.ImMessageHistoryEntity;
import com.dh.message.dao.mapper.ImGroupMessageHistoryMapper;
import com.dh.message.dao.mapper.ImMessageBodyMapper;
import com.dh.message.dao.mapper.ImMessageHistoryMapper;
import com.dh.message.model.DoStoreGroupMessageDto;
import com.dh.message.model.DoStoreP2PMessageDto;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StoreMessageService {

    @Autowired
    private ImMessageHistoryMapper imMessageHistoryMapper;

    @Autowired
    private ImMessageBodyMapper imMessageBodyMapper;

    @Autowired
    private ImGroupMessageHistoryMapper imGroupMessageHistoryMapper;

    public void doStoreP2PMessage(DoStoreP2PMessageDto dto) {
        imMessageBodyMapper.insert(dto.getImMessageBodyEntity());

        List<ImMessageHistoryEntity> imMessageHistoryEntities =
                extractToP2PMessageHistory(dto.getMessageContent(), dto.getImMessageBodyEntity());

        imMessageHistoryMapper.insertBatchSomeColumn(imMessageHistoryEntities);

    }

    @Transactional
    public List<ImMessageHistoryEntity> extractToP2PMessageHistory(MessageContent messageContent,
                                                                    ImMessageBodyEntity imMessageBodyEntity){
        List<ImMessageHistoryEntity> list = new ArrayList<>();
        ImMessageHistoryEntity fromHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,fromHistory);
        fromHistory.setOwnerId(messageContent.getFromId());
        fromHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        fromHistory.setCreateTime(System.currentTimeMillis());
        fromHistory.setSequence(messageContent.getMessageSequence());

        ImMessageHistoryEntity toHistory = new ImMessageHistoryEntity();
        BeanUtils.copyProperties(messageContent,toHistory);
        toHistory.setOwnerId(messageContent.getToId());
        toHistory.setMessageKey(imMessageBodyEntity.getMessageKey());
        toHistory.setCreateTime(System.currentTimeMillis());
        toHistory.setSequence(messageContent.getMessageSequence());


        list.add(fromHistory);
        list.add(toHistory);
        return list;
    }

    @Transactional
    public void doStoreGroupMessage(DoStoreGroupMessageDto dto) {
        imMessageBodyMapper.insert(dto.getImMessageBodyEntity());
        ImGroupMessageHistoryEntity imGroupMessageHistoryEntity =
                extractToGroupMessageHistory(dto.getGroupChatMessageContent(), dto.getImMessageBodyEntity());
        imGroupMessageHistoryMapper.insert(imGroupMessageHistoryEntity);
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
}
