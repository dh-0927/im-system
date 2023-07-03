package com.dh.im.service.message.service;

import com.dh.im.common.ResponseVO;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.enums.*;
import com.dh.im.service.friendship.dao.ImFriendShipEntity;
import com.dh.im.service.friendship.model.req.GetRelationReq;
import com.dh.im.service.friendship.service.ImFriendShipService;
import com.dh.im.service.group.dao.ImGroupEntity;
import com.dh.im.service.group.model.resp.GetRoleInGroupResp;
import com.dh.im.service.group.service.ImGroupMemberService;
import com.dh.im.service.group.service.ImGroupService;
import com.dh.im.service.user.dao.ImUserDataEntity;
import com.dh.im.service.user.service.ImUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CheckSendMessageService {

    @Autowired
    private ImUserService imUserService;

    @Autowired
    private ImFriendShipService imFriendShipService;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ImGroupService imGroupService;

    @Autowired
    private ImGroupMemberService imGroupMemberService;


    public ResponseVO checkSenderDisabledAndMute(String fromId, Integer appId) {

        ResponseVO<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(fromId, appId);
        if (!singleUserInfo.isOk()) {
            return singleUserInfo;
        }

        ImUserDataEntity user = singleUserInfo.getData();
        if (user.getForbiddenFlag() == UserForbiddenFlagEnum.FORBIDDEN.getCode()) {
            return ResponseVO.errorResponse(MessageErrorCode.FROMER_IS_FORBIDDEN);
        } else if (user.getSilentFlag() == UserSilentFlagEnum.MUTE.getCode()) {
            return ResponseVO.errorResponse(MessageErrorCode.FROMER_IS_MUTE);
        }

        return ResponseVO.successResponse();
    }

    public ResponseVO checkFriendShip(String fromId, String toId, Integer appId) {

        if (appConfig.isSendMessageCheckFriend()) {
            GetRelationReq fromReq = new GetRelationReq();
            fromReq.setFromId(fromId);
            fromReq.setToId(toId);
            fromReq.setAppId(appId);
            ResponseVO<ImFriendShipEntity> fromRelation = imFriendShipService.getRelationFriend(fromReq);
            if (!fromRelation.isOk()) {
                return fromRelation;
            }
            GetRelationReq toReq = new GetRelationReq();
            fromReq.setFromId(toId);
            fromReq.setToId(fromId);
            fromReq.setAppId(appId);
            ResponseVO<ImFriendShipEntity> toRelation = imFriendShipService.getRelationFriend(fromReq);
            if (!toRelation.isOk()) {
                return toRelation;
            }
            if (fromRelation.getData().getStatus()
                    != FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()) {
                return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_DELETED);
            }
            if (toRelation.getData().getStatus()
                    != FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode()) {
                return ResponseVO.errorResponse(FriendShipErrorCode.TARGET_IS_DELETED_YOU);
            }
            if (appConfig.isSendMessageCheckBlack()) {
                if (fromRelation.getData().getBlack()
                        != FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()) {
                    return ResponseVO.errorResponse(FriendShipErrorCode.FRIEND_IS_BLACK);
                }
                if (toRelation.getData().getBlack()
                        != FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode()) {
                    return ResponseVO.errorResponse(FriendShipErrorCode.TARGET_IS_BLACK_YOU);

                }
            }

        }
        return ResponseVO.successResponse();
    }

    public ResponseVO checkGroupMessage(String fromId, String groupId, Integer appId) {

        ResponseVO responseVO = checkSenderDisabledAndMute(fromId, appId);
        if (!responseVO.isOk()) {
            return responseVO;
        }

        // 判断群逻辑
        ResponseVO<ImGroupEntity> group = imGroupService.getGroup(groupId, appId);
        if (!group.isOk()) {
            return group;
        }

        ResponseVO<GetRoleInGroupResp> roleInGroupOne = imGroupMemberService.getRoleInGroupOne(groupId, fromId, appId);
        if (!roleInGroupOne.isOk()) {
            return roleInGroupOne;
        }
        GetRoleInGroupResp memberData = roleInGroupOne.getData();

        // 判断群是否被禁言，如果禁言，只有群主和群管理可以发消息
        ImGroupEntity groupData = group.getData();
        if (groupData.getMute() == GroupMuteTypeEnum.MUTE.getCode()
                && memberData.getRole() == GroupMemberRoleEnum.ORDINARY.getCode()) {
            return ResponseVO.errorResponse(GroupErrorCode.THIS_GROUP_IS_MUTE);
        }

        if (memberData.getSpeakDate() != null &&
                memberData.getSpeakDate() > System.currentTimeMillis()) {
            return ResponseVO.errorResponse(GroupErrorCode.GROUP_MEMBER_IS_SPEAK);
        }

        return ResponseVO.successResponse();
    }

}
