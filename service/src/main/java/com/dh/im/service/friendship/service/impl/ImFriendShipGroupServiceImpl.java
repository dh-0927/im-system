package com.dh.im.service.friendship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.dh.im.codec.park.friendship.AddFriendGroupPack;
import com.dh.im.codec.park.friendship.DeleteFriendGroupPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.enums.DelFlagEnum;
import com.dh.im.common.enums.FriendShipErrorCode;
import com.dh.im.common.enums.command.FriendshipEventCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.service.friendship.dao.ImFriendShipGroupEntity;
import com.dh.im.service.friendship.dao.mapper.ImFriendShipGroupMapper;
import com.dh.im.service.friendship.model.req.AddFriendShipGroupMemberReq;
import com.dh.im.service.friendship.model.req.AddFriendShipGroupReq;
import com.dh.im.service.friendship.model.req.DeleteFriendShipGroupReq;
import com.dh.im.service.friendship.service.ImFriendShipGroupMemberService;
import com.dh.im.service.friendship.service.ImFriendShipGroupService;
import com.dh.im.service.utils.MessageProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ImFriendShipGroupServiceImpl implements ImFriendShipGroupService {

    @Autowired
    private ImFriendShipGroupMapper imFriendShipGroupMapper;

    @Autowired
    @Lazy
    private ImFriendShipGroupMemberService imFriendShipGroupMemberService;

    @Autowired
    private MessageProducer messageProducer;


    @Override
    @Transactional
    public ResponseVO addGroup(AddFriendShipGroupReq req) {

        LambdaQueryWrapper<ImFriendShipGroupEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImFriendShipGroupEntity::getGroupName, req.getGroupName());
        lqw.eq(ImFriendShipGroupEntity::getAppId, req.getAppId());
        lqw.eq(ImFriendShipGroupEntity::getFromId, req.getFromId());
//        lqw.eq(ImFriendShipGroupEntity::getDelFlag, DelFlagEnum.NORMAL.getCode());

        ImFriendShipGroupEntity entity = imFriendShipGroupMapper.selectOne(lqw);
        if (entity == null) {
            //写入db
            entity = new ImFriendShipGroupEntity();
            entity.setAppId(req.getAppId());
            long time = System.currentTimeMillis();
            entity.setCreateTime(time);
            entity.setUpdateTime(time);
            entity.setDelFlag(DelFlagEnum.NORMAL.getCode());
            entity.setGroupName(req.getGroupName());
            entity.setFromId(req.getFromId());
//            try {
            if (imFriendShipGroupMapper.insert(entity) != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_CREATE_ERROR);
            }
        } else {
            if (entity.getDelFlag() == DelFlagEnum.NORMAL.getCode()) {
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_IS_EXIST);
            }
            ImFriendShipGroupEntity updateEntity = new ImFriendShipGroupEntity();
            updateEntity.setGroupId(entity.getGroupId());
            updateEntity.setUpdateTime(System.currentTimeMillis());
            updateEntity.setDelFlag(DelFlagEnum.NORMAL.getCode());
            if (imFriendShipGroupMapper.updateById(updateEntity) != 1) {
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_CREATE_ERROR);
            }
        }

        if (CollectionUtils.isNotEmpty(req.getToIds())) {
            AddFriendShipGroupMemberReq addFriendShipGroupMemberReq = new AddFriendShipGroupMemberReq();
            addFriendShipGroupMemberReq.setFromId(req.getFromId());
            addFriendShipGroupMemberReq.setGroupName(req.getGroupName());
            addFriendShipGroupMemberReq.setToIds(req.getToIds());
            addFriendShipGroupMemberReq.setAppId(req.getAppId());
            return imFriendShipGroupMemberService.addGroupMember(addFriendShipGroupMemberReq);
        }

        AddFriendGroupPack addFriendGropPack = new AddFriendGroupPack();
        addFriendGropPack.setFromId(req.getFromId());
        addFriendGropPack.setGroupName(req.getGroupName());
//        addFriendGropPack.setSequence(seq);
        messageProducer.sendToUserExceptClient(req.getFromId(), FriendshipEventCommand.FRIEND_GROUP_ADD,
                addFriendGropPack,new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()));

        return ResponseVO.successResponse();
}


    @Override
    @Transactional
    public ResponseVO deleteGroup(DeleteFriendShipGroupReq req) {

        List<String> successName = new ArrayList<>();

        for (String groupName : req.getGroupName()) {

            LambdaQueryWrapper<ImFriendShipGroupEntity> lqw = new LambdaQueryWrapper<>();
            lqw.eq(ImFriendShipGroupEntity::getGroupName, groupName);
            lqw.eq(ImFriendShipGroupEntity::getAppId, req.getAppId());
            lqw.eq(ImFriendShipGroupEntity::getFromId, req.getFromId());
            lqw.eq(ImFriendShipGroupEntity::getDelFlag, DelFlagEnum.NORMAL.getCode());


            ImFriendShipGroupEntity entity = imFriendShipGroupMapper.selectOne(lqw);


            if (entity != null) {
                ImFriendShipGroupEntity update = new ImFriendShipGroupEntity();
                update.setGroupId(entity.getGroupId());
                update.setUpdateTime(System.currentTimeMillis());
                update.setDelFlag(DelFlagEnum.DELETE.getCode());
                imFriendShipGroupMapper.updateById(update);
                imFriendShipGroupMemberService.clearGroupMember(entity.getGroupId());
                successName.add(groupName);
            }

            DeleteFriendGroupPack deleteFriendGroupPack = new DeleteFriendGroupPack();
            deleteFriendGroupPack.setFromId(req.getFromId());
            deleteFriendGroupPack.setGroupName(groupName);
//            deleteFriendGroupPack.setSequence(seq);
            //TCP通知
            messageProducer.sendToUserExceptClient(req.getFromId(), FriendshipEventCommand.FRIEND_GROUP_DELETE,
                    deleteFriendGroupPack,new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()));
        }

        if (successName.size() == 0) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(successName);
    }

    @Override
    public ResponseVO getGroup(String fromId, String groupName, Integer appId) {
        LambdaQueryWrapper<ImFriendShipGroupEntity> lqw = new LambdaQueryWrapper<>();

        lqw.eq(ImFriendShipGroupEntity::getGroupName, groupName);
        lqw.eq(ImFriendShipGroupEntity::getAppId, appId);
        lqw.eq(ImFriendShipGroupEntity::getFromId, fromId);
        lqw.eq(ImFriendShipGroupEntity::getDelFlag, DelFlagEnum.NORMAL.getCode());

        ImFriendShipGroupEntity entity = imFriendShipGroupMapper.selectOne(lqw);
        if (entity == null) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_GROUP_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(entity);
    }

}
