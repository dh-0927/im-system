package com.dh.im.service.friendship.controller;

import com.dh.im.common.ResponseVO;
import com.dh.im.service.friendship.dao.mapper.ImFriendShipRequestMapper;
import com.dh.im.service.friendship.model.req.*;
import com.dh.im.service.friendship.service.ImFriendShipRequestService;
import com.dh.im.service.friendship.service.ImFriendShipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/v1/friendshipRequest")
public class ImFriendShipRequestController {

    @Autowired
    private ImFriendShipRequestService imFriendShipRequestService;


    @RequestMapping("/approveFriendRequest")
    public ResponseVO approveFriendRequest(@RequestBody @Validated ApproveFriendRequestReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return imFriendShipRequestService.approveFriendRequest(req);
    }

    @RequestMapping("/getFriendRequest")
    public ResponseVO getFriendRequest(@RequestBody @Validated GetFriendShipRequestReq req, Integer appId) {
        req.setAppId(appId);
        return imFriendShipRequestService.getFriendRequest(req.getFromId(), req.getAppId());
    }

    @RequestMapping("/readFriendShipRequestReq")
    public ResponseVO readFriendShipRequestReq(@RequestBody @Validated ReadFriendShipRequestReq req, Integer appId) {
        req.setAppId(appId);
        return imFriendShipRequestService.readFriendShipRequestReq(req);
    }

}
