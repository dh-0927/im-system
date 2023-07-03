package com.dh.im.service.group.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dh.im.codec.park.group.CreateGroupPack;
import com.dh.im.codec.park.group.DestroyGroupPack;
import com.dh.im.codec.park.group.UpdateGroupInfoPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.*;
import com.dh.im.common.enums.command.GroupEventCommand;
import com.dh.im.common.exception.ApplicationException;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.SyncReq;
import com.dh.im.common.model.SyncResp;
import com.dh.im.service.conversation.dao.ImConversationSetEntity;
import com.dh.im.service.group.dao.ImGroupEntity;
import com.dh.im.service.group.dao.mapper.ImGroupMapper;
import com.dh.im.service.group.model.callback.DestroyGroupCallbackDto;
import com.dh.im.service.group.model.req.*;
import com.dh.im.service.group.model.resp.GetGroupInfoResp;
import com.dh.im.service.group.model.resp.GetRoleInGroupResp;
import com.dh.im.service.group.service.ImGroupMemberService;
import com.dh.im.service.group.service.ImGroupService;
import com.dh.im.service.seq.RedisSeq;
import com.dh.im.service.user.service.ImUserService;
import com.dh.im.service.utils.CallbackService;
import com.dh.im.service.utils.GroupMessageProducer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ImGroupServiceImpl implements ImGroupService {

    @Autowired
    private ImGroupMapper imGroupMapper;

    @Autowired
    @Lazy
    private ImGroupMemberService imGroupMemberService;

    @Autowired
    private ImUserService imUserService;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private CallbackService callbackService;

    @Autowired
    private GroupMessageProducer groupMessageProducer;

    @Autowired
    private RedisSeq redisSeq;

    @Override
    public ResponseVO importGroup(ImportGroupReq req) {
        String groupId = req.getGroupId();
        if (StringUtils.isNotBlank(groupId)) {
            LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
            // 判断群id是否在系统中已经存在
            lqw.eq(ImGroupEntity::getGroupId, groupId)
                    .eq(ImGroupEntity::getAppId, req.getAppId());
            if (imGroupMapper.selectCount(lqw) > 0) {
                return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_EXIST);
            }
        } else {
            // 生成群id
            req.setGroupId(UUID.randomUUID().toString().replace("-", ""));
        }
        // 判断是否是公共群，公共群必须设置群主
        if (req.getGroupType() == GroupTypeEnum.PUBLIC.getCode()) {
            if (req.getOwnerId() == null) {
                return ResponseVO.errorResponse(GroupErrorCode.PUBLIC_GROUP_MUST_HAVE_OWNER);
            }
            // 判断群主是否存在
            if (!imUserService.getSingleUserInfo(req.getOwnerId(), req.getAppId()).isOk()) {
                return ResponseVO.errorResponse(GroupErrorCode.PUBLIC_GROUP_OWNER_NOT_EXISTS);
            }
        }

        ImGroupEntity entity = new ImGroupEntity();
        BeanUtils.copyProperties(req, entity);
        if (req.getCreateTime() == null) {
            long time = System.currentTimeMillis();
            entity.setCreateTime(time);
            entity.setUpdateTime(time);
        }
        if (req.getStatus() == null) {
            entity.setStatus(GroupStatusEnum.NORMAL.getCode());
        }
        if (req.getMute() == null) {
            entity.setMute(GroupMuteTypeEnum.NOT_MUTE.getCode());
        }
        if (imGroupMapper.insert(entity) == 1) {
            if (req.getGroupType() == GroupTypeEnum.PUBLIC.getCode()) {
                GroupMemberDto groupMemberDto = new GroupMemberDto();
                groupMemberDto.setMemberId(req.getOwnerId());
                groupMemberDto.setRole(GroupMemberRoleEnum.OWNER.getCode());
                groupMemberDto.setJoinTime(System.currentTimeMillis());
                imGroupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), groupMemberDto);
            }
            return ResponseVO.successResponse();
        }
        return ResponseVO.errorResponse(GroupErrorCode.IMPORT_GROUP_ERROR);
    }

    @Override
    @Transactional
    public ResponseVO createGroup(CreateGroupReq req) {

        boolean isAdmin = false;

        if (!isAdmin) {
            req.setOwnerId(req.getOperator());
        }

        LambdaQueryWrapper<ImGroupEntity> query = new LambdaQueryWrapper<>();

        //1.判断群id是否存在
        if (StringUtils.isEmpty(req.getGroupId())) {
            req.setGroupId(UUID.randomUUID().toString().replace("-", ""));
        } else {
            query.eq(ImGroupEntity::getGroupId, req.getGroupId());
            query.eq(ImGroupEntity::getAppId, req.getAppId());
            if (imGroupMapper.selectCount(query) > 0) {
                // 如果群已经存在
                return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_EXIST);
            }
        }


        if (req.getGroupType() == GroupTypeEnum.PUBLIC.getCode() && StringUtils.isBlank(req.getOwnerId())) {
            // 如果是公共群并且没有设置群主，返回
            return ResponseVO.errorResponse(GroupErrorCode.PUBLIC_GROUP_MUST_HAVE_OWNER);
        }

        // 插入群数据
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        ImGroupEntity imGroupEntity = new ImGroupEntity();
        long time = System.currentTimeMillis();
        imGroupEntity.setCreateTime(time);
        imGroupEntity.setUpdateTime(time);
        imGroupEntity.setSequence(seq);
        imGroupEntity.setStatus(GroupStatusEnum.NORMAL.getCode());
        BeanUtils.copyProperties(req, imGroupEntity);
        imGroupMapper.insert(imGroupEntity);

//         如果有群主，插入群主数据
        if (StringUtils.isNotBlank(req.getOwnerId())) {
            GroupMemberDto groupMemberDto = new GroupMemberDto();
            groupMemberDto.setMemberId(req.getOwnerId());
            groupMemberDto.setRole(GroupMemberRoleEnum.OWNER.getCode());
            groupMemberDto.setJoinTime(time);
            imGroupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), groupMemberDto);
        }

        //插入群成员
        for (GroupMemberDto dto : req.getMember()) {
            imGroupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), dto);
        }

        if (appConfig.isCreateGroupAfterCallback()) {
            callbackService.callback(req.getAppId(), Constants.CallbackCommand.CreateGroupAfter,
                    JSONObject.toJSONString(imGroupEntity));
        }

        CreateGroupPack createGroupPack = new CreateGroupPack();
        BeanUtils.copyProperties(imGroupEntity, createGroupPack);
        groupMessageProducer.producer(req.getOperator(), GroupEventCommand.CREATED_GROUP, createGroupPack
                , new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO<ImGroupEntity> getGroup(String groupId, Integer appId) {
        LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupEntity::getGroupId, groupId)
                .eq(ImGroupEntity::getAppId, appId);
        ImGroupEntity imGroupEntity = imGroupMapper.selectOne(lqw);
        if (imGroupEntity == null) {
            return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }
        return ResponseVO.successResponse(imGroupEntity);
    }

    @Override
    public ResponseVO updateGroupInfo(UpdateGroupInfoReq req) {
        // 首先获取群是否存在
        String groupId = req.getGroupId();
        Integer appId = req.getAppId();
        ResponseVO<ImGroupEntity> group = getGroup(groupId, appId);
        if (!group.isOk()) {
            return group;
        }
        // 标识是否是后台管理员操作
        boolean isAdmin = false;
        if (!isAdmin) {
            // 不是管理员，需要校验权限
            // 得到操作用户对应的权限
            ResponseVO<GetRoleInGroupResp> response = imGroupMemberService.getRoleInGroupOne(groupId, req.getOperator(), appId);
            if (!response.isOk()) {
                return response;
            }
            Integer role = response.getData().getRole();
            // 如果是公共群，只有群主和管理员才能修改群信息
            if (group.getData().getGroupType() == GroupTypeEnum.PUBLIC.getCode()) {
                if (role != GroupMemberRoleEnum.MANAGER.getCode() && role != GroupMemberRoleEnum.OWNER.getCode()) {
                    return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                }
            }

        }
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        ImGroupEntity entity = new ImGroupEntity();
        BeanUtils.copyProperties(req, entity);
        entity.setSequence(seq);
        LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupEntity::getAppId, appId)
                .eq(ImGroupEntity::getGroupId, groupId);
        if (imGroupMapper.update(entity, lqw) != 1) {
            return ResponseVO.errorResponse(GroupErrorCode.UPDATE_GROUP_BASE_INFO_ERROR);
        }
        if (appConfig.isModifyGroupAfterCallback()) {
            callbackService.callback(req.getAppId(), Constants.CallbackCommand.UpdateGroupAfter,
                    JSONObject.toJSONString(imGroupMapper.selectOne(lqw)));
        }

        UpdateGroupInfoPack pack = new UpdateGroupInfoPack();
        BeanUtils.copyProperties(req, pack);
        pack.setSequence(seq);
        groupMessageProducer.producer(req.getOperator(), GroupEventCommand.UPDATED_GROUP,
                pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));


        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO getJoinedGroup(GetJoinedGroupReq req) {
        // 判断用户存不存在
        if (!imUserService.getSingleUserInfo(req.getMemberId(), req.getAppId()).isOk()) {
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }
        // 获取当前用户所有加入的指定类型群组
        ResponseVO<Set<String>> response =
                imGroupMemberService.getGroupIdByMemberId(req.getMemberId(), req.getAppId());
        // 根据id查询对应的群信息

        LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupEntity::getAppId, req.getAppId())
                .in(CollectionUtils.isNotEmpty(req.getGroupType()), ImGroupEntity::getGroupType, req.getGroupType())
                .in(ImGroupEntity::getGroupId, response.getData());

        List<ImGroupEntity> groupEntityList = imGroupMapper.selectList(lqw);

        return ResponseVO.successResponse(groupEntityList);
    }

    @Override
    public ResponseVO getGroupInfo(GetGroupInfoReq req) {

        // 判断群是否存在
        ResponseVO group = getGroup(req.getGroupId(), req.getAppId());
        if (!group.isOk()) {
            return group;
        }

        GetGroupInfoResp getGroupResp = new GetGroupInfoResp();
        BeanUtils.copyProperties(group.getData(), getGroupResp);
        try {
            // 群存在，找出该群的所有用户
            ResponseVO<List<GroupMemberDto>> groupMember = imGroupMemberService.getGroupMember(req.getGroupId(), req.getAppId());
            if (groupMember.isOk()) {
                getGroupResp.setMemberList(groupMember.getData());
            }
        } catch (Exception e) {

            e.printStackTrace();
        }
        return ResponseVO.successResponse(getGroupResp);
    }

    @Override
    @Transactional
    public ResponseVO destroyGroup(DestroyGroupReq req) {

        boolean isAdmin = false;

        LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
        String groupId = req.getGroupId();
        lqw.eq(ImGroupEntity::getGroupId, groupId);
        Integer appId = req.getAppId();
        lqw.eq(ImGroupEntity::getAppId, appId);
        ImGroupEntity imGroupEntity = imGroupMapper.selectOne(lqw);
        // 群不存在
        if (imGroupEntity == null) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }

        // 群已解散
        if (imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        // 判断是否是管理员操作
        if (!isAdmin) {
            // 不是，校验权限
            // 私有群只允许平台管理员解散
            if (imGroupEntity.getGroupType() == GroupTypeEnum.PRIVATE.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
            }

            // 公共群只允许群主解散
            if (imGroupEntity.getGroupType() == GroupTypeEnum.PUBLIC.getCode() &&
                    !imGroupEntity.getOwnerId().equals(req.getOperator())) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }
        }
        // 需要将所有成员移出群聊
        ResponseVO response = imGroupMemberService.removeAllMember(groupId, appId);
        if (!response.isOk()) {
            return response;
        }

        // 更新群信息为销毁
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        ImGroupEntity update = new ImGroupEntity();
        update.setSequence(seq);
        update.setStatus(GroupStatusEnum.DESTROY.getCode());
        if (imGroupMapper.update(update, lqw) != 1) {
            throw new ApplicationException(GroupErrorCode.UPDATE_GROUP_BASE_INFO_ERROR);
        }


        if (appConfig.isModifyGroupAfterCallback()) {
            DestroyGroupCallbackDto dto = new DestroyGroupCallbackDto();
            dto.setGroupId(req.getGroupId());
            callbackService.callback(req.getAppId()
                    , Constants.CallbackCommand.DestroyGroupAfter,
                    JSONObject.toJSONString(dto));
        }

        DestroyGroupPack pack = new DestroyGroupPack();
        pack.setSequence(seq);
        pack.setGroupId(req.getGroupId());
        groupMessageProducer.producer(req.getOperator(),
                GroupEventCommand.DESTROY_GROUP, pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));

        return ResponseVO.successResponse();
    }

    @Override
    @Transactional
    public ResponseVO transferGroup(TransferGroupReq req) {

        String groupId = req.getGroupId();
        Integer appId = req.getAppId();
        // 判断群是否存在
        ResponseVO<ImGroupEntity> group = getGroup(groupId, appId);
        if (!group.isOk()) {
            return group;
        }

        // 获取操作人的角色
        ResponseVO<GetRoleInGroupResp> roleInGroupOne = imGroupMemberService.getRoleInGroupOne(groupId, req.getOperator(), appId);
        if (!roleInGroupOne.isOk()) {
            return roleInGroupOne;
        }

        // 操作人必须是群主
        if (roleInGroupOne.getData().getRole() != GroupMemberRoleEnum.OWNER.getCode()) {
            return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
        }

        // 获取被转让人信息，必须在群内
        String ownerId = req.getOwnerId();
        ResponseVO<GetRoleInGroupResp> newOwnerRole = imGroupMemberService.getRoleInGroupOne(groupId, ownerId, appId);
        if (!newOwnerRole.isOk()) {
            return newOwnerRole;
        }

        // 获取群消息
        LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupEntity::getGroupId, groupId);
        lqw.eq(ImGroupEntity::getAppId, appId);
        ImGroupEntity imGroupEntity = imGroupMapper.selectOne(lqw);
        // 如果已经被销毁
        if (imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + Constants.SeqConstants.Group);
        ImGroupEntity updateGroup = new ImGroupEntity();
        updateGroup.setSequence(seq);
        updateGroup.setOwnerId(ownerId);
        LambdaQueryWrapper<ImGroupEntity> updateLqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupEntity::getGroupId, groupId);
        lqw.eq(ImGroupEntity::getAppId, appId);
        // 更新im_group表修改群主id
        imGroupMapper.update(updateGroup, updateLqw);
        // 更新im_group_member表修改被转让人角色为群主
        imGroupMemberService.transferGroupMember(ownerId, groupId, appId);

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO muteGroup(MuteGroupReq req) {

        ResponseVO<ImGroupEntity> groupResp = getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        if (groupResp.getData().getStatus() == GroupStatusEnum.DESTROY.getCode()) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        boolean isAdmin = false;

        if (!isAdmin) {
            //不是后台调用需要检查权限
            ResponseVO<GetRoleInGroupResp> role =
                    imGroupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperator(), req.getAppId());

            if (!role.isOk()) {
                return role;
            }

            Integer roleInfo = role.getData().getRole();

            if (roleInfo != GroupMemberRoleEnum.OWNER.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }

        }

        ImGroupEntity update = new ImGroupEntity();
        update.setMute(req.getMute());

        UpdateWrapper<ImGroupEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("group_id", req.getGroupId());
        wrapper.eq("app_id", req.getAppId());
        imGroupMapper.update(update, wrapper);

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO syncJoinedGroupList(SyncReq req) {
        if (req.getMaxLimit() > 100) {
            req.setMaxLimit(100);
        }

        SyncResp<ImGroupEntity> resp = new SyncResp<>();
        ResponseVO<Collection<String>> memberJoinedGroup =
                imGroupMemberService.syncMemberJoinedGroup(req.getOperator(), req.getAppId());
        if (memberJoinedGroup.isOk()) {
            Collection<String> data = memberJoinedGroup.getData();
            LambdaQueryWrapper<ImGroupEntity> lqw = new LambdaQueryWrapper<>();
            lqw.eq(ImGroupEntity::getAppId, req.getAppId())
                    .in(ImGroupEntity::getGroupId, data)
                    .gt(ImGroupEntity::getSequence, req.getLastSequence())
                    .last("limit " + req.getMaxLimit())
                    .orderByAsc(ImGroupEntity::getSequence);

            List<ImGroupEntity> list = imGroupMapper.selectList(lqw);

            if (CollectionUtils.isNotEmpty(list)) {
                ImGroupEntity maxSeqEntity = list.get(list.size() - 1);
                resp.setDataList(list);

                Long maxSeq = imGroupMapper.getGroupMaxSeq(data, req.getAppId());
                resp.setMaxSequence(maxSeq);

                resp.setCompleted(maxSeqEntity.getSequence() >= maxSeq);
                return ResponseVO.successResponse(resp);
            }
        }
        resp.setCompleted(true);
        return ResponseVO.successResponse(resp);
    }

    @Override
    public Long getUserGroupMaxSeq(String userId, Integer appId) {
        ResponseVO<Collection<String>> memberJoinedGroup =
                imGroupMemberService.syncMemberJoinedGroup(userId, appId);
        if (!memberJoinedGroup.isOk()) {
            throw new ApplicationException(500, "");
        }
        Collection<String> data = memberJoinedGroup.getData();
        return imGroupMapper.getGroupMaxSeq(data, appId);
    }


}
