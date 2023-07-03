package com.dh.im.service.user.controller;

import com.dh.im.common.ClientType;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.route.RouteHandle;
import com.dh.im.common.route.RouteInfo;
import com.dh.im.common.route.algorithm.ramdom.RandomHandle;
import com.dh.im.common.utils.RouteInfoParseUtil;
import com.dh.im.service.user.model.req.*;
import com.dh.im.service.user.model.resp.UserOnlineStatusResp;
import com.dh.im.service.user.service.ImUserService;
import com.dh.im.service.user.service.ImUserStatusService;
import com.dh.im.service.utils.ZKit;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
public class ImUserController {

    @Autowired
    private ImUserService imUserService;

    @Autowired
    private RouteHandle routeHandle;

    @Autowired
    private ImUserStatusService imUserStatusService;

    @Autowired
    private ZKit zKit;


    @RequestMapping("/importUser")
    public ResponseVO importUser(@RequestBody ImportUserReq req, Integer appId) {
        req.setAppId(appId);
        return imUserService.importUser(req);
    }

    @RequestMapping("/deleteUser")
    public ResponseVO deleteUser(@RequestBody @Validated DeleteUserReq req, Integer appId) {
        req.setAppId(appId);
        return imUserService.deleteUser(req);
    }

    // Im的登陆接口，返回im地址
    @RequestMapping("/login")
    public ResponseVO<RouteInfo> login(@RequestBody LoginReq req, Integer appId) {
        req.setAppId(appId);
        ResponseVO<RouteInfo> login = imUserService.login(req);
        if (login.isOk()) {
            List<String> allNode;
            if (req.getClientType() == ClientType.WEB.getCode()) {
                allNode = zKit.getAllWebNode();
            } else {
                allNode = zKit.getAllTcpNode();
            }
            String ipPort = routeHandle.routeServer(allNode, req.getUserId());
            RouteInfo parse = RouteInfoParseUtil.parse(ipPort);
            return ResponseVO.successResponse(parse);
        }
        return ResponseVO.errorResponse();
    }

    @RequestMapping("/getUserSequence")
    public ResponseVO getUserSequence(@RequestBody @Validated GetUserSequenceReq req, Integer appId) {
        req.setAppId(appId);
        return imUserService.getUserSequence(req);
    }

    @RequestMapping("/subscribeUserOnlineStatus")
    public ResponseVO subscribeUserOnlineStatus(@RequestBody @Validated SubscribeUserOnlineStatusReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        imUserStatusService.subscribeUserOnlineStatus(req);
        return ResponseVO.successResponse();
    }

    @RequestMapping("/setUserCustomerStatus")
    public ResponseVO setUserCustomerStatus(@RequestBody @Validated
                                            SetUserCustomerStatusReq req, Integer appId,String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        imUserStatusService.setUserCustomerStatus(req);
        return ResponseVO.successResponse();
    }

    @RequestMapping("/queryFriendOnlineStatus")
    public ResponseVO<Map<String, UserOnlineStatusResp>> queryFriendOnlineStatus(@RequestBody @Validated
                                              PullFriendOnlineStatusReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return ResponseVO.successResponse(imUserStatusService.queryFriendOnlineStatus(req));
    }

    @RequestMapping("/queryUserOnlineStatus")
    public ResponseVO<Map<String, UserOnlineStatusResp>> queryUserOnlineStatus(@RequestBody @Validated
                                            PullUserOnlineStatusReq req, Integer appId,String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return ResponseVO.successResponse(imUserStatusService.queryUserOnlineStatus(req));
    }



}
