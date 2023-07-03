package com.dh.im.service.friendship.service;


import com.dh.im.common.ResponseVO;
import com.dh.im.common.model.RequestBase;
import com.dh.im.common.model.SyncReq;
import com.dh.im.service.friendship.dao.ImFriendShipEntity;
import com.dh.im.service.friendship.model.req.*;
import com.dh.im.service.friendship.model.resp.CheckFriendShipResp;

import java.util.List;

public interface ImFriendShipService {

    ResponseVO importFriendShip(ImportFriendShipReq req);

    ResponseVO addFriend(AddFriendReq req);

    ResponseVO doAddFriend(RequestBase requestBase, String fromId, FriendDto friendDto, Integer appId);
    ResponseVO updateFriend(UpdateFriend req);

    ResponseVO deleteFriend(DeleteFriendReq req);

    ResponseVO deleteAllFriend(DeleteFriendReq req);

    ResponseVO<ImFriendShipEntity> getRelationFriend(GetRelationReq req);

    ResponseVO<List<ImFriendShipEntity>> getAllFriendShip(GetAllFriendShipReq req);

    ResponseVO<List<CheckFriendShipResp>> checkFriendShip(CheckFriendShipReq req);

    ResponseVO addBlack(AddFriendShipBlackReq req);

    ResponseVO deleteBlack(DeleteBlackReq req);

    ResponseVO checkBlack(CheckFriendShipReq req);

    ResponseVO syncFriendshipList(SyncReq req);
}
