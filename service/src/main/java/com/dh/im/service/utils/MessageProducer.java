package com.dh.im.service.utils;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.proto.MessagePack;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.command.Command;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class MessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private UserSessionUtils userSessionUtils;

    private String exchangeName = Constants.RabbitConstants.MessageService2Im;

    public boolean sendMessage(UserSession session, Object msg) {
        try {
            log.info("send message == {}", msg);
            rabbitTemplate.convertAndSend(exchangeName, session.getBrokerId() + "", msg);
            return true;
        } catch (Exception e) {
            log.error("send error : {}", e.getMessage());
            return false;
        }
    }

    // 包装数据，调用sendMessage
    public boolean sendPack(String toId, Command command, Object msg, UserSession session) {

        MessagePack<JSONObject> messagePack = new MessagePack<>();
        messagePack.setCommand(command.getCommand());
        messagePack.setUserId(session.getUserId());
        messagePack.setToId(toId);
        messagePack.setClientType(session.getClientType());
        messagePack.setImei(session.getImei());
        messagePack.setAppId(session.getAppId());

        JSONObject jsonObject = JSONObject.parseObject(JSONObject.toJSONString(msg));
        messagePack.setData(jsonObject);

        return sendMessage(session, JSONObject.toJSONString(messagePack));
    }

    // 发送给某个用户所有端
    public List<ClientInfo> sendToUser(String toId, Command command, Object data, Integer appId) {
        List<UserSession> userSessions = userSessionUtils.getUserSession(appId, toId);
        List<ClientInfo> list = new ArrayList<>();
        for (UserSession userSession: userSessions){
            if (sendPack(toId, command, data, userSession)) {
                list.add(
                        new ClientInfo(
                                userSession.getAppId(),
                                userSession.getClientType(),
                                userSession.getImei())
                );
            }
        }
        return list;
    }

    public void sendToUser(String toId, Integer clientType,String imei, Command command,
                           Object data, Integer appId){
        if(clientType != null && StringUtils.isNotBlank(imei)){
            ClientInfo clientInfo = new ClientInfo(appId, clientType, imei);
            sendToUserExceptClient(toId,command,data,clientInfo);
        }else{
            sendToUser(toId,command,data,appId);
        }
    }

    // 发送给某个用户的指定端
    public void sendToUser(String toId, Command command, Object data, ClientInfo clientInfo) {
        UserSession session = userSessionUtils.getUserSession(clientInfo.getAppId(),
                toId, clientInfo.getClientType(), clientInfo.getImei());
        sendPack(toId, command, data, session);
    }

    private boolean isMatch(UserSession sessionDto, ClientInfo clientInfo) {
        return Objects.equals(sessionDto.getAppId(), clientInfo.getAppId())
                && Objects.equals(sessionDto.getImei(), clientInfo.getImei())
                && Objects.equals(sessionDto.getClientType(), clientInfo.getClientType());
    }

    //发送给除了某一端的其他端
    public void sendToUserExceptClient(String toId, Command command, Object data, ClientInfo clientInfo){
        List<UserSession> userSession =
                userSessionUtils.getUserSession(clientInfo.getAppId(), toId);
        for (UserSession session : userSession) {
            if(!isMatch(session,clientInfo)){
                sendPack(toId,command,data,session);
            }
        }
    }

}
