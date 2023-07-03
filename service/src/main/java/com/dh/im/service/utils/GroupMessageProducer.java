package com.dh.im.service.utils;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.park.group.AddGroupMemberPack;
import com.dh.im.codec.park.group.RemoveGroupMemberPack;
import com.dh.im.codec.park.group.UpdateGroupMemberPack;
import com.dh.im.common.ClientType;
import com.dh.im.common.enums.command.Command;
import com.dh.im.common.enums.command.GroupEventCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.service.group.model.req.GroupMemberDto;
import com.dh.im.service.group.service.ImGroupMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GroupMessageProducer {

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private ImGroupMemberService imGroupMemberService;

    public void producer(String userId, Command command, Object data, ClientInfo clientInfo) {

        JSONObject o = (JSONObject) JSONObject.toJSON(data);
        String groupId = o.getString("groupId");

        List<String> groupManager =
                imGroupMemberService.getGroupManager(groupId, clientInfo.getAppId());

        if (command.equals(GroupEventCommand.ADDED_MEMBER)) {
            // 发送给管理员和被加入人本身
            AddGroupMemberPack addGroupMemberPack =
                    o.toJavaObject(AddGroupMemberPack.class);
            List<String> members = addGroupMemberPack.getMembers();

            for (String managerId : groupManager) {
                if (clientInfo.getClientType() != ClientType.WEBAPI.getCode()
                        && managerId.equals(userId)) {
                    messageProducer.sendToUserExceptClient(managerId, command, data, clientInfo);
                } else {
                    messageProducer.sendToUser(managerId, command, data, clientInfo.getAppId());
                }
            }
            for (String member : members) {
                if (clientInfo.getClientType() != ClientType.WEBAPI.getCode() && member.equals(userId)) {
                    messageProducer.sendToUserExceptClient(member, command, data, clientInfo);
                } else {
                    messageProducer.sendToUser(member, command, data, clientInfo.getAppId());
                }
            }
        } else if (command.equals(GroupEventCommand.DELETED_MEMBER)) {
            RemoveGroupMemberPack pack = o.toJavaObject(RemoveGroupMemberPack.class);
            String member = pack.getMember();
            List<String> members = imGroupMemberService.getGroupMemberId(groupId, clientInfo.getAppId());
            members.add(member);
            for (String memberId : members) {
                if (clientInfo.getClientType() != ClientType.WEBAPI.getCode()
                        && member.equals(userId)) {
                    messageProducer.sendToUserExceptClient(memberId, command, data, clientInfo);
                } else {
                    messageProducer.sendToUser(memberId, command, data, clientInfo.getAppId());
                }
            }
        } else if (command.equals(GroupEventCommand.UPDATED_MEMBER)) {
            UpdateGroupMemberPack pack =
                    o.toJavaObject(UpdateGroupMemberPack.class);
            String memberId = pack.getMemberId();
            groupManager.add(memberId);
            for (String managerId : groupManager) {
                if (clientInfo.getClientType() != ClientType.WEBAPI.getCode() && managerId.equals(userId)) {
                    messageProducer.sendToUserExceptClient(managerId, command, data, clientInfo);
                } else {
                    messageProducer.sendToUser(managerId, command, data, clientInfo.getAppId());
                }
            }
        } else {
            for (String memberId : groupManager) {
                if (clientInfo.getClientType() != null
                        && clientInfo.getClientType() != ClientType.WEBAPI.getCode()
                        && memberId.equals(userId)) {
                    messageProducer.sendToUserExceptClient(memberId, command,
                            data, clientInfo);
                } else {
                    messageProducer.sendToUser(memberId, command, data, clientInfo.getAppId());
                }
            }
        }

    }
}
