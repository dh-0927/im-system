package com.dh.im.service.group.service;

import com.dh.im.common.ResponseVO;
import com.dh.im.common.model.SyncReq;
import com.dh.im.service.group.dao.ImGroupEntity;
import com.dh.im.service.group.model.req.*;

public interface ImGroupService {

    ResponseVO importGroup(ImportGroupReq req);

    ResponseVO createGroup(CreateGroupReq req);

    ResponseVO<ImGroupEntity> getGroup(String groupId, Integer appId);

    ResponseVO updateGroupInfo(UpdateGroupInfoReq req);

    ResponseVO getJoinedGroup(GetJoinedGroupReq req);

    ResponseVO getGroupInfo(GetGroupInfoReq req);

    ResponseVO destroyGroup(DestroyGroupReq req);

    ResponseVO transferGroup(TransferGroupReq req);

    ResponseVO muteGroup(MuteGroupReq req);


    ResponseVO syncJoinedGroupList(SyncReq req);

    Long getUserGroupMaxSeq(String userId, Integer appId);
}
