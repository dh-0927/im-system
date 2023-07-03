package com.dh.im.service.group.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.*;
import com.dh.im.common.exception.ApplicationException;
import com.dh.im.service.group.dao.ImGroupEntity;
import com.dh.im.service.group.dao.ImGroupMemberEntity;
import com.dh.im.service.group.dao.mapper.ImGroupMemberMapper;
import com.dh.im.service.group.model.callback.AddMemberAfterCallback;
import com.dh.im.service.group.model.req.*;
import com.dh.im.service.group.model.resp.AddMemberResp;
import com.dh.im.service.group.model.resp.GetRoleInGroupResp;
import com.dh.im.service.group.service.ImGroupMemberService;
import com.dh.im.service.group.service.ImGroupService;
import com.dh.im.service.user.dao.ImUserDataEntity;
import com.dh.im.service.user.service.ImUserService;
import com.dh.im.service.utils.CallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.dh.im.common.ResponseVO.successResponse;

@Slf4j
@Service
public class ImGroupMemberServiceImpl implements ImGroupMemberService {

    @Autowired
    private ImGroupMemberMapper imGroupMemberMapper;

    @Autowired
    private ImGroupService imGroupService;

    @Autowired
    private ImUserService imUserService;

    @Autowired
    @Lazy
    private ImGroupMemberService thisService;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private CallbackService callbackService;


    @Override
    public ResponseVO importGroupMember(ImportGroupMemberReq req) {

        List<AddMemberResp> respList = new ArrayList<>();

        // 判断群是否存在
        String groupId = req.getGroupId();
        Integer appId = req.getAppId();
        ResponseVO group = imGroupService.getGroup(groupId, appId);
        if (!group.isOk()) {
            return group;
        }

        req.getMembers().forEach(member -> {

            AddMemberResp addMemberResp = null;

            ResponseVO responseAdd = thisService.addGroupMember(groupId, appId, member);


            String memberId = member.getMemberId();
            if (responseAdd.isOk()) {
                // 加群成功
                addMemberResp = AddMemberResp.success(memberId);
            } else if (responseAdd.getCode() == GroupErrorCode.USER_IS_JOINED_GROUP.getCode()) {
                // 以在群中
                addMemberResp = AddMemberResp.exists(memberId);
            } else {
                // 其他错误
                addMemberResp = AddMemberResp.error(memberId, responseAdd.getMsg());
            }
            respList.add(addMemberResp);

        });

        return successResponse(respList);
    }

    /**
     * 添加群成员，拉人入群的逻辑，直接进入群聊。如果是后台管理员，则直接拉入群，
     * 否则只有私有群可以调用本接口，并且群成员也可以拉人入群.只有私有群可以调用本接口
     */
    @Override
    public ResponseVO addMember(AddGroupMemberReq req) {

        boolean isAdmin = false;
        // 判断群是否存在
        String operatorId = req.getOperator();
        String groupId = req.getGroupId();
        Integer appId = req.getAppId();
        ResponseVO<ImGroupEntity> groupResp = imGroupService.getGroup(groupId, appId);
        if (!groupResp.isOk()) {
            return groupResp;
        }


        List<GroupMemberDto> memberDtos = req.getMembers();
        if(appConfig.isAddGroupMemberBeforeCallback()){

            ResponseVO responseVO = callbackService.beforeCallback(req.getAppId(), Constants.CallbackCommand.GroupMemberAddBefore
                    , JSONObject.toJSONString(req));
            if(!responseVO.isOk()){
                return responseVO;
            }
            try {
                memberDtos = JSONArray.parseArray
                        (JSONObject.toJSONString(responseVO.getData()), GroupMemberDto.class);
            }catch (Exception e){
                e.printStackTrace();
                log.error("GroupMemberAddBefore 回调失败：{}",req.getAppId());
            }
        }

        // 判断操作人是否存在
        if (!imUserService.getSingleUserInfo(operatorId, appId).isOk()) {
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }

        LambdaQueryWrapper<ImGroupMemberEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupMemberEntity::getAppId, appId)
                .eq(ImGroupMemberEntity::getGroupId, groupId)
                .eq(ImGroupMemberEntity::getMemberId, operatorId);

        // 判断操作人是否在群中
        ImGroupMemberEntity entity = imGroupMemberMapper.selectOne(lqw);
        // 如果没有查到或者该用户已退群
        if (entity == null || (entity.getRole() != null && entity.getRole() == GroupMemberRoleEnum.LEAVE.getCode())) {
            return ResponseVO.errorResponse(GroupErrorCode.MEMBER_IS_NOT_JOINED_GROUP);
        }

        ImGroupEntity group = groupResp.getData();
        /**
         * 私有群（private）	类似普通微信群，创建后仅支持已在群内的好友邀请加群，且无需被邀请方同意或群主审批
         * 公开群（Public）	类似 QQ 群，创建后群主可以指定群管理员，需要群主或管理员审批通过才能入群
         * 群类型 1私有群（类似微信） 2公开群(类似qq）
         *
         */
        // 如果是公开群只能由系统管理员直接拉人进群
        if (!isAdmin && GroupTypeEnum.PUBLIC.getCode() == group.getGroupType()) {
            throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
        }

        // 响应集
        List<AddMemberResp> respList = new ArrayList<>();
        memberDtos.forEach(member -> {

            AddMemberResp addMemberResp = null;

            // 邀请的人默认为普通成员
            member.setRole(GroupMemberRoleEnum.ORDINARY.getCode());

            ResponseVO responseAdd = thisService.addGroupMember(groupId, appId, member);

            String memberId = member.getMemberId();
            if (responseAdd.isOk()) {
                // 加群成功
                addMemberResp = AddMemberResp.success(memberId);
            } else if (responseAdd.getCode() == GroupErrorCode.USER_IS_JOINED_GROUP.getCode()) {
                // 以在群中
                addMemberResp = AddMemberResp.exists(memberId);
            } else {
                // 其他错误
                addMemberResp = AddMemberResp.error(memberId, responseAdd.getMsg());
            }
            respList.add(addMemberResp);

        });


        if(appConfig.isAddGroupMemberAfterCallback()){
            AddMemberAfterCallback dto = new AddMemberAfterCallback();
            dto.setGroupId(req.getGroupId());
            dto.setGroupType(group.getGroupType());
            dto.setMemberId(respList);
            dto.setOperater(req.getOperator());
            callbackService.callback(req.getAppId()
                    ,Constants.CallbackCommand.GroupMemberAddAfter,
                    JSONObject.toJSONString(dto));
        }

        return ResponseVO.successResponse(respList);
    }

    @Override
    public ResponseVO removeMember(RemoveGroupMemberReq req) {

        List<AddMemberResp> resp = new ArrayList<>();
        boolean isAdmin = false;
        // 判断群是否存在
        ResponseVO<ImGroupEntity> groupResp = imGroupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        ImGroupEntity group = groupResp.getData();
        if (!isAdmin) {

            //获取操作人的权限 是管理员or群主or群成员
            ResponseVO<GetRoleInGroupResp> role = getRoleInGroupOne(req.getGroupId(), req.getOperator(), req.getAppId());
            if (!role.isOk()) {
                return role;
            }

            Integer roleInfo = role.getData().getRole();

            boolean isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
            boolean isManager = roleInfo == GroupMemberRoleEnum.MANAGER.getCode();

            // 既不是管理员又不是群主
            if (!isOwner && !isManager) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

            //私有群必须是群主才能踢人
            if (!isOwner && GroupTypeEnum.PRIVATE.getCode() == group.getGroupType()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }

            //公开群管理员和群主可踢人，但管理员只能踢普通群成员
            if (GroupTypeEnum.PUBLIC.getCode() == group.getGroupType()) {
//                    throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                //获取被踢人的权限
                ResponseVO<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
                if (!roleInGroupOne.isOk()) {
                    return roleInGroupOne;
                }
                GetRoleInGroupResp memberRole = roleInGroupOne.getData();
                // 不能移除群主
                if (memberRole.getRole() == GroupMemberRoleEnum.OWNER.getCode()) {
                    throw new ApplicationException(GroupErrorCode.GROUP_OWNER_IS_NOT_REMOVE);
                }
                // 是管理员并且被踢人不是普通成员，无法操作
                if (isManager && memberRole.getRole() != GroupMemberRoleEnum.ORDINARY.getCode()) {
                    throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                }
            }
        }
        removeGroupMember(req.getGroupId(), req.getAppId(), req.getMemberId());
        if(appConfig.isDeleteGroupMemberAfterCallback()){
            callbackService.callback(req.getAppId(),
                    Constants.CallbackCommand.GroupMemberDeleteAfter,
                    JSONObject.toJSONString(req));
        }
        return ResponseVO.successResponse();
    }

    /**
     * 删除群成员，内部调用
     */
    @Override
    public ResponseVO removeGroupMember(String groupId, Integer appId, String memberId) {

        // 判断用户是否存在
        ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(memberId, appId);
        if (!singleUserInfo.isOk()) {
            return singleUserInfo;
        }

        // 判断是否在群中，在的话拿到信息
        ResponseVO<GetRoleInGroupResp> roleInGroupOne = getRoleInGroupOne(groupId, memberId, appId);
        if (!roleInGroupOne.isOk()) {
            return roleInGroupOne;
        }

        GetRoleInGroupResp data = roleInGroupOne.getData();

        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        imGroupMemberEntity.setRole(GroupMemberRoleEnum.LEAVE.getCode());
        imGroupMemberEntity.setLeaveTime(System.currentTimeMillis());
        imGroupMemberEntity.setGroupMemberId(data.getGroupMemberId());
        imGroupMemberMapper.updateById(imGroupMemberEntity);
        return ResponseVO.successResponse();
    }


    @Override
    @Transactional
    public ResponseVO addGroupMember(String groupId, Integer appId, GroupMemberDto dto) {

        // 判断该用户是否存在
        ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(dto.getMemberId(), appId);
        if (!singleUserInfo.isOk()) {
            return singleUserInfo;
        }

        // 如果是插入群主数据，判断是否已有群主
        if (dto.getRole() != null && GroupMemberRoleEnum.OWNER.getCode() == dto.getRole()) {
            LambdaQueryWrapper<ImGroupMemberEntity> queryOwner = new LambdaQueryWrapper<>();
            queryOwner.eq(ImGroupMemberEntity::getGroupId, groupId);
            queryOwner.eq(ImGroupMemberEntity::getAppId, appId);
            queryOwner.eq(ImGroupMemberEntity::getRole, GroupMemberRoleEnum.OWNER.getCode());
            Long ownerNum = imGroupMemberMapper.selectCount(queryOwner);
            // 如果已有群主，返回插入错误信息
            if (ownerNum > 0) {
                return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_HAVE_OWNER);
            }
        }

        // 判断该群中是否有该用户
        LambdaQueryWrapper<ImGroupMemberEntity> query = new LambdaQueryWrapper<>();
        query.eq(ImGroupMemberEntity::getGroupId, groupId);
        query.eq(ImGroupMemberEntity::getAppId, appId);
        query.eq(ImGroupMemberEntity::getMemberId, dto.getMemberId());
        ImGroupMemberEntity memberDto = imGroupMemberMapper.selectOne(query);

        long now = System.currentTimeMillis();
        if (memberDto == null) {
            // 没有记录，初次加群
            memberDto = new ImGroupMemberEntity();
            BeanUtils.copyProperties(dto, memberDto);
            memberDto.setGroupId(groupId);
            memberDto.setAppId(appId);
            memberDto.setJoinTime(now);
            int insert = imGroupMemberMapper.insert(memberDto);
            if (insert == 1) {
                return successResponse();
            }
            return ResponseVO.errorResponse(GroupErrorCode.USER_JOIN_GROUP_ERROR);
        } else if (GroupMemberRoleEnum.LEAVE.getCode() == memberDto.getRole()) {
            // 有记录，离开过群，重新进群
            memberDto = new ImGroupMemberEntity();
            BeanUtils.copyProperties(dto, memberDto);
            memberDto.setJoinTime(now);
            int update = imGroupMemberMapper.update(memberDto, query);
            if (update == 1) {
                return successResponse();
            }
            return ResponseVO.errorResponse(GroupErrorCode.USER_JOIN_GROUP_ERROR);
        }

        // 用户已经加入该群
        return ResponseVO.errorResponse(GroupErrorCode.USER_IS_JOINED_GROUP);

    }

    @Override
    public ResponseVO<GetRoleInGroupResp> getRoleInGroupOne(String groupId, String memberId, Integer appId) {
        // 判断用户是否存在
        if (!imUserService.getSingleUserInfo(memberId, appId).isOk()) {
            return ResponseVO.errorResponse(UserErrorCode.USER_IS_NOT_EXIST);
        }
        ResponseVO<ImGroupEntity> group = imGroupService.getGroup(groupId, appId);
        if (!group.isOk()) {
            return ResponseVO.errorResponse(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }
        LambdaQueryWrapper<ImGroupMemberEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupMemberEntity::getAppId, appId)
                .eq(ImGroupMemberEntity::getGroupId, groupId)
                .eq(ImGroupMemberEntity::getMemberId, memberId);

        ImGroupMemberEntity entity = imGroupMemberMapper.selectOne(lqw);
        // 如果没有查到或者该用户已退群
        if (entity == null || (entity.getRole() != null && entity.getRole() == GroupMemberRoleEnum.LEAVE.getCode())) {
            return ResponseVO.errorResponse(GroupErrorCode.MEMBER_IS_NOT_JOINED_GROUP);
        }
        GetRoleInGroupResp response = new GetRoleInGroupResp();
        BeanUtils.copyProperties(entity, response);

        return ResponseVO.successResponse(response);
    }

    @Override
    public ResponseVO<List<GroupMemberDto>> getGroupMember(String groupId, Integer appId) {
        LambdaQueryWrapper<ImGroupMemberEntity> lqw = new LambdaQueryWrapper<>();

        lqw.eq(ImGroupMemberEntity::getAppId, appId)
                .eq(ImGroupMemberEntity::getGroupId, groupId)
                .in(ImGroupMemberEntity::getRole,
                        GroupMemberRoleEnum.ORDINARY.getCode(),
                        GroupMemberRoleEnum.MANAGER.getCode(),
                        GroupMemberRoleEnum.OWNER.getCode());

        List<GroupMemberDto> dtoList = imGroupMemberMapper.selectList(lqw).stream()
                .map(entity -> {
                    GroupMemberDto dto = new GroupMemberDto();
                    BeanUtils.copyProperties(entity, dto);
                    return dto;
                }).collect(Collectors.toList());

//                .(ImGroupMemberEntity::getRole, )
        return ResponseVO.successResponse(dtoList);
    }

    @Override
    public ResponseVO<Set<String>> getGroupIdByMemberId(String memberId, Integer appId) {
        LambdaQueryWrapper<ImGroupMemberEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupMemberEntity::getAppId, appId)
                .eq(ImGroupMemberEntity::getMemberId, memberId)
                .in(ImGroupMemberEntity::getRole,
                        GroupMemberRoleEnum.ORDINARY.getCode(),
                        GroupMemberRoleEnum.MANAGER.getCode(),
                        GroupMemberRoleEnum.OWNER.getCode());
        Set<String> collect = imGroupMemberMapper.selectList(lqw).stream()
                .map(ImGroupMemberEntity::getGroupId)
                .collect(Collectors.toSet());
        return ResponseVO.successResponse(collect);
    }

    @Override
    @Transactional
    public ResponseVO transferGroupMember(String owner, String groupId, Integer appId) {

        //更新旧群主，将其角色更改为普通成员
        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        imGroupMemberEntity.setRole(GroupMemberRoleEnum.ORDINARY.getCode());
        UpdateWrapper<ImGroupMemberEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("app_id", appId);
        updateWrapper.eq("group_id", groupId);
        updateWrapper.eq("role", GroupMemberRoleEnum.OWNER.getCode());
        imGroupMemberMapper.update(imGroupMemberEntity, updateWrapper);

        //更新新群主
        ImGroupMemberEntity newOwner = new ImGroupMemberEntity();
        newOwner.setRole(GroupMemberRoleEnum.OWNER.getCode());
        UpdateWrapper<ImGroupMemberEntity> ownerWrapper = new UpdateWrapper<>();
        ownerWrapper.eq("app_id", appId);
        ownerWrapper.eq("group_id", groupId);
        ownerWrapper.eq("member_id", owner);
        imGroupMemberMapper.update(newOwner, ownerWrapper);

        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO removeAllMember(String groupId, Integer appId) {
        // 判断群是否存在
        ResponseVO<ImGroupEntity> response = imGroupService.getGroup(groupId, appId);
        if (!response.isOk()) {
            return response;
        }
        // 将所有成员移出群
        LambdaUpdateWrapper<ImGroupMemberEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ImGroupMemberEntity::getGroupId, groupId)
                .eq(ImGroupMemberEntity::getAppId, appId)
                .set(ImGroupMemberEntity::getLeaveTime, System.currentTimeMillis())
                .set(ImGroupMemberEntity::getRole, GroupMemberRoleEnum.LEAVE.getCode());

        try {
            imGroupMemberMapper.update(null, updateWrapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ResponseVO.successResponse();

    }

    @Override
    public ResponseVO exitGroup(ExitGroupReq req) {
        String groupId = req.getGroupId();
        Integer appId = req.getAppId();
        String memberId = req.getOperator();

        // 获取用户在群中的权限时会判断用户是否存在，群是否存在，用户是否在群中
        ResponseVO<GetRoleInGroupResp> roleInGroup = getRoleInGroupOne(groupId, memberId, appId);
        // 如果退出的是群主
        if (roleInGroup.getData().getRole() == GroupMemberRoleEnum.OWNER.getCode()) {
            return ResponseVO.errorResponse(GroupErrorCode.OWNER_ONLY_DESTORY_GROUP);
        }
        // 正常退出
        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();

        imGroupMemberEntity.setRole(GroupMemberRoleEnum.LEAVE.getCode());
        imGroupMemberEntity.setLeaveTime(System.currentTimeMillis());
        imGroupMemberEntity.setGroupMemberId(roleInGroup.getData().getGroupMemberId());
        imGroupMemberMapper.updateById(imGroupMemberEntity);
        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO updateGroupMember(UpdateGroupMemberReq req) {
        boolean isAdmin = false;

        String groupId = req.getGroupId();
        Integer appId = req.getAppId();
        // 判断群是否存在，拿到群基本信息
        ResponseVO<ImGroupEntity> group = imGroupService.getGroup(groupId, appId);
        if (!group.isOk()) {
            return group;
        }

        ImGroupEntity groupData = group.getData();
        // 判断群是否已解散
        if (groupData.getStatus() == GroupStatusEnum.DESTROY.getCode()) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        //是否是自己修改自己的资料
        boolean isMeOperate = req.getOperator().equals(req.getMemberId());

        if (!isAdmin) {
            //昵称只能自己修改 权限只能群主或管理员修改
            if (StringUtils.isNotBlank(req.getAlias()) && !isMeOperate) {
                return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_ONESELF);
            }
            //私有群不能设置管理员
            if (groupData.getGroupType() == GroupTypeEnum.PRIVATE.getCode() &&
                    req.getRole() != null && (req.getRole() == GroupMemberRoleEnum.MANAGER.getCode())) {
                return ResponseVO.errorResponse(GroupErrorCode.PRIVATE_GROUP_CAN_NOT_MANAGER);
            }

            //如果要修改权限相关的则走下面的逻辑
            if (req.getRole() != null) {
                //获取被操作人的是否在群内
                ResponseVO<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(groupId, req.getMemberId(), appId);
                if (!roleInGroupOne.isOk()) {
                    return roleInGroupOne;
                }

                //获取操作人权限
                ResponseVO<GetRoleInGroupResp> operateRoleInGroupOne = this.getRoleInGroupOne(groupId, req.getOperator(), appId);
                if (!operateRoleInGroupOne.isOk()) {
                    return operateRoleInGroupOne;
                }

                GetRoleInGroupResp data = operateRoleInGroupOne.getData();
                Integer roleInfo = data.getRole();
                boolean isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
                boolean isManager = roleInfo == GroupMemberRoleEnum.MANAGER.getCode();

                //不是管理员不能修改权限
                if (!isOwner && !isManager) {
                    return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                }

                //管理员只有群主能够设置
                if (req.getRole() == GroupMemberRoleEnum.MANAGER.getCode() && !isOwner) {
                    return ResponseVO.errorResponse(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                }

            }
        }

        ImGroupMemberEntity update = new ImGroupMemberEntity();

        if (StringUtils.isNotBlank(req.getAlias())) {
            update.setAlias(req.getAlias());
        }

        //不能直接修改为群主
        if (req.getRole() != null && req.getRole() != GroupMemberRoleEnum.OWNER.getCode()) {
            update.setRole(req.getRole());
        }

        UpdateWrapper<ImGroupMemberEntity> objectUpdateWrapper = new UpdateWrapper<>();
        objectUpdateWrapper.eq("app_id", appId);
        objectUpdateWrapper.eq("member_id", req.getMemberId());
        objectUpdateWrapper.eq("group_id", groupId);
        imGroupMemberMapper.update(update, objectUpdateWrapper);


        return ResponseVO.successResponse();
    }

    @Override
    public ResponseVO speak(SpeakMemberReq req) {

        ResponseVO<ImGroupEntity> groupResp = imGroupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        boolean isAdmin = false;
        boolean isOwner = false;
        boolean isManager = false;
        GetRoleInGroupResp memberRole = null;

        if (!isAdmin) {

            //获取操作人的权限 是管理员or群主or群成员
            ResponseVO<GetRoleInGroupResp> role = getRoleInGroupOne(req.getGroupId(), req.getOperator(), req.getAppId());
            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
            isManager = roleInfo == GroupMemberRoleEnum.MANAGER.getCode();

            if (!isOwner && !isManager) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

            //获取被操作的权限
            ResponseVO<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
            if (!roleInGroupOne.isOk()) {
                return roleInGroupOne;
            }
            memberRole = roleInGroupOne.getData();
            //被操作人是群主只能app管理员操作
            if (memberRole.getRole() == GroupMemberRoleEnum.OWNER.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
            }

            //是管理员并且被操作人不是普通群成员，无法操作
            if (isManager && memberRole.getRole() != GroupMemberRoleEnum.ORDINARY.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }
        }

        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
//        if(memberRole == null){
//            //获取被操作的权限
//            ResponseVO<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
//            if (!roleInGroupOne.isOk()) {
//                return roleInGroupOne;
//            }
//            memberRole = roleInGroupOne.getData();
//        }

        imGroupMemberEntity.setGroupMemberId(memberRole.getGroupMemberId());
        if(req.getSpeakDate() > 0){
            imGroupMemberEntity.setSpeakDate(System.currentTimeMillis() + req.getSpeakDate());
        }else{
            imGroupMemberEntity.setSpeakDate(req.getSpeakDate());
        }

        imGroupMemberMapper.updateById(imGroupMemberEntity);
        return ResponseVO.successResponse();
    }

    @Override
    public List<String> getGroupManager(String groupId, Integer appId) {
        LambdaQueryWrapper<ImGroupMemberEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupMemberEntity::getAppId, appId)
                .eq(ImGroupMemberEntity::getGroupId, groupId)
                .in(ImGroupMemberEntity::getRole, GroupMemberRoleEnum.MANAGER, GroupMemberRoleEnum.OWNER);
        return imGroupMemberMapper.selectList(lqw)
                .stream()
                .map(ImGroupMemberEntity::getGroupId)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getGroupMemberId(String groupId, Integer appId) {
        LambdaQueryWrapper<ImGroupMemberEntity> lqw = new LambdaQueryWrapper<>();
        lqw.eq(ImGroupMemberEntity::getAppId, appId)
                .eq(ImGroupMemberEntity::getGroupId, groupId)
                .ne(ImGroupMemberEntity::getRole, GroupMemberRoleEnum.LEAVE.getCode());
//                .in(ImGroupMemberEntity::getRole, GroupMemberRoleEnum.MANAGER, GroupMemberRoleEnum.OWNER);
        return imGroupMemberMapper.selectList(lqw)
                .stream()
                .map(ImGroupMemberEntity::getMemberId)
                .collect(Collectors.toList());
    }

    @Override
    public ResponseVO<Collection<String>> syncMemberJoinedGroup(String operator, Integer appId) {
        return ResponseVO.successResponse(
                imGroupMemberMapper.syncJoinedGroupId(
                        appId,
                        operator,
                        GroupMemberRoleEnum.LEAVE.getCode()));
    }


}
