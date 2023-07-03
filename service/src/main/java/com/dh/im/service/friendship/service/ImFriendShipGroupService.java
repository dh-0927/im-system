package com.dh.im.service.friendship.service;


import com.dh.im.common.ResponseVO;
import com.dh.im.service.friendship.dao.ImFriendShipGroupEntity;
import com.dh.im.service.friendship.model.req.AddFriendShipGroupReq;
import com.dh.im.service.friendship.model.req.DeleteFriendShipGroupReq;


public interface ImFriendShipGroupService {

    ResponseVO addGroup(AddFriendShipGroupReq req);

    ResponseVO deleteGroup(DeleteFriendShipGroupReq req);

    ResponseVO<ImFriendShipGroupEntity> getGroup(String fromId, String groupName, Integer appId);

}
