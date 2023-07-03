package com.dh.im.service.group.controller;

import com.dh.im.common.ResponseVO;
import com.dh.im.common.model.SyncReq;
import com.dh.im.common.model.message.CheckGroupSendMessageReq;
import com.dh.im.common.model.message.CheckSendMessageReq;
import com.dh.im.service.group.model.req.*;
import com.dh.im.service.group.service.GroupMessageService;
import com.dh.im.service.group.service.ImGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/group")
public class ImGroupController {
    @Autowired
    private ImGroupService imGroupService;

    @Autowired
    private GroupMessageService groupMessageService;


    @RequestMapping("/importGroup")
    public ResponseVO importGroup(@RequestBody @Validated ImportGroupReq req, Integer appId) {
        req.setAppId(appId);
        return imGroupService.importGroup(req);
    }

    @RequestMapping("/update")
    public ResponseVO update(@RequestBody @Validated UpdateGroupInfoReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return imGroupService.updateGroupInfo(req);
    }

    @RequestMapping("/create")
    public ResponseVO create(@RequestBody @Validated CreateGroupReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return imGroupService.createGroup(req);
    }

    @RequestMapping("/getGroupInfo")
    public ResponseVO getGroupInfo(@RequestBody @Validated GetGroupInfoReq req, Integer appId) {
        req.setAppId(appId);
        return imGroupService.getGroupInfo(req);
    }


    @RequestMapping("/getJoinedGroup")
    public ResponseVO getJoinedGroup(@RequestBody @Validated GetJoinedGroupReq req, Integer appId) {
        req.setAppId(appId);
        return imGroupService.getJoinedGroup(req);
    }

    @RequestMapping("/destroyGroup")
    public ResponseVO destroyGroup(@RequestBody @Validated DestroyGroupReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return imGroupService.destroyGroup(req);
    }

    @RequestMapping("/transferGroup")
    public ResponseVO transferGroup(@RequestBody @Validated TransferGroupReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return imGroupService.transferGroup(req);
    }

    @RequestMapping("/muteGroup")
    public ResponseVO muteGroup(@RequestBody @Validated MuteGroupReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return imGroupService.muteGroup(req);
    }

    @RequestMapping("/sendMessage")
    public ResponseVO sendMessage(@RequestBody @Validated SendGroupMessageReq req, Integer appId, String identifier) {
        req.setAppId(appId);
        req.setOperator(identifier);
        return ResponseVO.successResponse(groupMessageService.send(req));
    }

    @RequestMapping("/checkGroupSend")
    public ResponseVO checkGroupSend(@RequestBody @Validated CheckGroupSendMessageReq req) {
        return groupMessageService.imServerPermissionCheck(req.getFromId(), req.getGroupId(), req.getAppId());
    }

    @RequestMapping("/syncJoinedGroup")
    public ResponseVO syncJoinedGroup(@RequestBody @Validated SyncReq req, Integer appId, String identifier)  {
        req.setAppId(appId);
        return imGroupService.syncJoinedGroupList(req);
    }

}
