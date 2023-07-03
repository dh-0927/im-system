package com.dh.im.service.friendship.service;

import com.dh.im.common.ResponseVO;
import com.dh.im.service.friendship.model.req.ApproveFriendRequestReq;
import com.dh.im.service.friendship.model.req.FriendDto;
import com.dh.im.service.friendship.model.req.ReadFriendShipRequestReq;

public interface ImFriendShipRequestService {

    ResponseVO addFriendshipRequest(String fromId, FriendDto dto, Integer appId);

    ResponseVO approveFriendRequest(ApproveFriendRequestReq req);

    ResponseVO readFriendShipRequestReq(ReadFriendShipRequestReq req);

    ResponseVO getFriendRequest(String fromId, Integer appId);
}
