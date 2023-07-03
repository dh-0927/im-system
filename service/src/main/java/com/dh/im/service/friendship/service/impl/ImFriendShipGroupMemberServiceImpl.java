package com.dh.im.service.friendship.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dh.im.codec.park.friendship.AddFriendGroupMemberPack;
import com.dh.im.codec.park.friendship.DeleteFriendGroupMemberPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.enums.FriendShipErrorCode;
import com.dh.im.common.enums.command.FriendshipEventCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.service.friendship.dao.ImFriendShipGroupEntity;
import com.dh.im.service.friendship.dao.ImFriendShipGroupMemberEntity;
import com.dh.im.service.friendship.dao.mapper.ImFriendShipGroupMemberMapper;
import com.dh.im.service.friendship.model.req.AddFriendShipGroupMemberReq;
import com.dh.im.service.friendship.model.req.CheckFriendShipReq;
import com.dh.im.service.friendship.model.req.DeleteFriendShipGroupMemberReq;
import com.dh.im.service.friendship.model.resp.CheckFriendShipResp;
import com.dh.im.service.friendship.service.ImFriendShipGroupMemberService;
import com.dh.im.service.friendship.service.ImFriendShipGroupService;
import com.dh.im.service.friendship.service.ImFriendShipService;
import com.dh.im.service.user.dao.ImUserDataEntity;
import com.dh.im.service.user.service.ImUserService;
import com.dh.im.service.utils.MessageProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ImFriendShipGroupMemberServiceImpl implements ImFriendShipGroupMemberService {

    @Autowired
    private ImFriendShipGroupMemberMapper imFriendShipGroupMemberMapper;

    @Autowired
    private ImFriendShipGroupService imFriendShipGroupService;

    @Autowired
    private ImFriendShipService imFriendShipService;

    @Autowired
    private ImUserService imUserService;

    @Autowired
    private MessageProducer messageProducer;


    @Override
    @Transactional
    public ResponseVO addGroupMember(AddFriendShipGroupMemberReq req) {

        Integer appId = req.getAppId();
        String groupName = req.getGroupName();
        String fromId = req.getFromId();
        ResponseVO<ImFriendShipGroupEntity> group = imFriendShipGroupService
                .getGroup(fromId, groupName, appId);
        if (!group.isOk()) {
            return group;
        }

        List<String> successId = new ArrayList<>();
        for (String toId : req.getToIds()) {
//            if (toId.equals(fromId)) {
//                continue;
//            }
            ResponseVO<ImUserDataEntity> userInfo = imUserService.getSingleUserInfo(toId, appId);

            if (userInfo.isOk()) {

                CheckFriendShipReq checkFriendShipReq = new CheckFriendShipReq();
                checkFriendShipReq.setAppId(appId);
                checkFriendShipReq.setCheckType(2);
                checkFriendShipReq.setFromId(fromId);
                checkFriendShipReq.setToIds(Collections.singletonList(toId));

                List<CheckFriendShipResp> data = imFriendShipService.checkFriendShip(checkFriendShipReq).getData();

                // 如果是双向好友
                if (data.get(0).getStatus() == 1) {
                    int i = doAddGroupMember(group.getData().getGroupId(), toId);
                    if (i == 1) {
                        successId.add(toId);
                    }
                }

                AddFriendGroupMemberPack pack = new AddFriendGroupMemberPack();
                pack.setFromId(req.getFromId());
                pack.setGroupName(req.getGroupName());
                pack.setToIds(successId);
//                pack.setSequence(seq);
                messageProducer.sendToUserExceptClient(req.getFromId(), FriendshipEventCommand.FRIEND_GROUP_MEMBER_ADD,
                        pack,new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()));

            }
        }

        if (successId.size() == 0) {
            return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_SHIP_ADD_GROUP_ALL_ERROR);
        }

        return ResponseVO.successResponse(successId);
    }

    @Override
    public ResponseVO delGroupMember(DeleteFriendShipGroupMemberReq req) {
        ResponseVO<ImFriendShipGroupEntity> group = imFriendShipGroupService
                .getGroup(req.getFromId(), req.getGroupName(), req.getAppId());
        if (!group.isOk()) {
            return group;
        }

        List<String> successId = new ArrayList<>();
        for (String toId : req.getToIds()) {
            ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(toId, req.getAppId());
            if (singleUserInfo.isOk()) {
                int i = deleteGroupMember(group.getData().getGroupId(), toId);
                if (i == 1) {
                    successId.add(toId);
                }
            }
        }

        DeleteFriendGroupMemberPack pack = new DeleteFriendGroupMemberPack();
        pack.setFromId(req.getFromId());
        pack.setGroupName(req.getGroupName());
        pack.setToIds(successId);
//        pack.setSequence(seq);
        messageProducer.sendToUserExceptClient(req.getFromId(), FriendshipEventCommand.FRIEND_GROUP_MEMBER_DELETE,
                pack,new ClientInfo(req.getAppId(),req.getClientType(),req.getImei()));

        return ResponseVO.successResponse(successId);
    }

    @Override
    public int doAddGroupMember(Long groupId, String toId) {
        ImFriendShipGroupMemberEntity imFriendShipGroupMemberEntity = new ImFriendShipGroupMemberEntity();
        imFriendShipGroupMemberEntity.setGroupId(groupId);
        imFriendShipGroupMemberEntity.setToId(toId);

        try {
            LambdaQueryWrapper<ImFriendShipGroupMemberEntity> lqw = new LambdaQueryWrapper<>();
            lqw.eq(ImFriendShipGroupMemberEntity::getGroupId, groupId);
            lqw.eq(ImFriendShipGroupMemberEntity::getToId, toId);
            if (imFriendShipGroupMemberMapper.selectOne(lqw) != null) {
                return 0;
            }
            return imFriendShipGroupMemberMapper.insert(imFriendShipGroupMemberEntity);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int deleteGroupMember(Long groupId, String toId) {
        QueryWrapper<ImFriendShipGroupMemberEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("group_id", groupId);
        queryWrapper.eq("to_id", toId);

        try {
            return imFriendShipGroupMemberMapper.delete(queryWrapper);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public int clearGroupMember(Long groupId) {
        QueryWrapper<ImFriendShipGroupMemberEntity> query = new QueryWrapper<>();
        query.eq("group_id", groupId);
        return imFriendShipGroupMemberMapper.delete(query);
    }
}
