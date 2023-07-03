package com.dh.im.service.group.service;

import com.dh.im.common.ResponseVO;
import com.dh.im.service.group.model.req.*;
import com.dh.im.service.group.model.resp.GetRoleInGroupResp;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ImGroupMemberService {

    ResponseVO importGroupMember(ImportGroupMemberReq req);

    // 拉人入群
    ResponseVO addMember(AddGroupMemberReq req);

    // 踢人
    ResponseVO removeMember(RemoveGroupMemberReq req);

    ResponseVO removeGroupMember(String groupId, Integer appId, String memberId);

    // 内部调用
    ResponseVO addGroupMember(String groupId, Integer appId, GroupMemberDto dto);

    ResponseVO<GetRoleInGroupResp> getRoleInGroupOne(String groupId, String memberId, Integer appId);

    ResponseVO<List<GroupMemberDto>> getGroupMember(String groupId, Integer appId);

    ResponseVO<Set<String>> getGroupIdByMemberId(String memberId, Integer appId);

    ResponseVO transferGroupMember(String owner, String groupId, Integer appId);

    ResponseVO removeAllMember(String groupId, Integer appId);

    ResponseVO exitGroup(ExitGroupReq req);

    ResponseVO updateGroupMember(UpdateGroupMemberReq req);

    ResponseVO speak(SpeakMemberReq req);

    List<String> getGroupManager(String groupId, Integer appId);

    List<String> getGroupMemberId(String groupId, Integer appId);

    ResponseVO<Collection<String>> syncMemberJoinedGroup(String operator, Integer appId);
}
