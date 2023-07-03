package com.dh.im.tcp.utils;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.park.user.UserStatusChangeNotifyPack;
import com.dh.im.codec.proto.MessageHeader;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ImConnectStatusEnum;
import com.dh.im.common.enums.command.UserEventCommand;
import com.dh.im.common.model.UserClientDto;
import com.dh.im.common.model.UserSession;
import com.dh.im.tcp.publish.MqMessageProducer;
import com.dh.im.tcp.redis.RedisManager;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SessionSocketHolder {

    private static final Map<UserClientDto, NioSocketChannel> CHANNELS = new ConcurrentHashMap<>();

    public static void put(Integer appId, Integer clientType, String imei,
                           String userId, NioSocketChannel channel) {

        UserClientDto userClientDto = new UserClientDto();
        userClientDto.setUserId(userId);
        userClientDto.setClientType(clientType);
        userClientDto.setAppId(appId);
        userClientDto.setImei(imei);

        CHANNELS.put(userClientDto, channel);
    }

    public static NioSocketChannel get(Integer appId, Integer clientType, String imei,
                                       String userId) {

        UserClientDto userClientDto = new UserClientDto();
        userClientDto.setUserId(userId);
        userClientDto.setClientType(clientType);
        userClientDto.setAppId(appId);
        userClientDto.setImei(imei);

        return CHANNELS.get(userClientDto);
    }

    public static List<NioSocketChannel> get(Integer appId , String id) {

        Set<UserClientDto> channelInfos = CHANNELS.keySet();
        List<NioSocketChannel> channels = new ArrayList<>();

        channelInfos.forEach(channel ->{
            if(channel.getAppId().equals(appId) && id.equals(channel.getUserId())){
                channels.add(CHANNELS.get(channel));
            }
        });

        return channels;
    }

    public static void remove(Integer appId, Integer clientType, String imei,
                              String userId) {

        UserClientDto userClientDto = new UserClientDto();
        userClientDto.setUserId(userId);
        userClientDto.setClientType(clientType);
        userClientDto.setAppId(appId);
        userClientDto.setImei(imei);

        CHANNELS.remove(userClientDto);
    }

    public static void remove(NioSocketChannel channel) {

        CHANNELS.entrySet().stream()
                .filter(entity -> entity.getValue() == channel)
                .forEach(entity -> CHANNELS.remove(entity.getKey()));

    }

    // 退出逻辑
    public static void removeUserSession(NioSocketChannel channel) {
        // 删除session
        String userId = (String) channel.attr(AttributeKey.valueOf(Constants.UserId)).get();
        Integer appId = (Integer) channel.attr(AttributeKey.valueOf(Constants.AppId)).get();
        Integer clientType = (Integer) channel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
        String imei = (String) channel.attr(AttributeKey.valueOf(Constants.Imei)).get();

        SessionSocketHolder.remove(appId, clientType, imei, userId);

        // 删除redis
        RedissonClient redissonClient = RedisManager.getRedissonClient();
        RMap<String, String> map = redissonClient.getMap
                (appId + Constants.RedisConstants.UserSessionConstants + userId);
        map.remove(clientType + ":" + imei);

        // 通知逻辑层
        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);

        MqMessageProducer.sendMessage(
                userStatusChangeNotifyPack,
                messageHeader,
                UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());
    }

    // 离线逻辑（删除内存中的channel, 将redis中的session状态改为离线）
    public static void offlineUserSession(NioSocketChannel channel) {
        // 删除session
        String userId = (String) channel.attr(AttributeKey.valueOf(Constants.UserId)).get();
        Integer appId = (Integer) channel.attr(AttributeKey.valueOf(Constants.AppId)).get();
        Integer clientType = (Integer) channel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
        String imei = (String) channel.attr(AttributeKey.valueOf(Constants.Imei)).get();

        SessionSocketHolder.remove(appId, clientType, imei, userId);

        // 修改redis
        RedissonClient redissonClient = RedisManager.getRedissonClient();
        RMap<String, String> map = redissonClient.getMap
                (appId + Constants.RedisConstants.UserSessionConstants + userId);

        // 获得
        String sessionStr = map.get(clientType.toString() + ":" + imei);
        if (StringUtils.isNotBlank(sessionStr)) {
            UserSession userSession = JSONObject.parseObject(sessionStr, UserSession.class);
            // 修改
            userSession.setConnectState(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
            // 写回
            map.put(clientType + ":" + imei, JSONObject.toJSONString(userSession));
        }

        // 通知逻辑层
        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);

        MqMessageProducer.sendMessage(
                userStatusChangeNotifyPack,
                messageHeader,
                UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());

        channel.close();
    }

}
