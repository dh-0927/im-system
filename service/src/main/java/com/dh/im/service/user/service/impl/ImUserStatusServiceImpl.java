package com.dh.im.service.user.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dh.im.codec.park.user.UserCustomStatusChangeNotifyPack;
import com.dh.im.codec.park.user.UserStatusChangeNotifyPack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.command.UserEventCommand;
import com.dh.im.common.model.ClientInfo;
import com.dh.im.common.model.UserSession;
import com.dh.im.service.friendship.dao.ImFriendShipEntity;
import com.dh.im.service.friendship.model.req.GetAllFriendShipReq;
import com.dh.im.service.friendship.service.ImFriendShipService;
import com.dh.im.service.user.model.UserStatusChangeNotifyContent;
import com.dh.im.service.user.model.req.PullFriendOnlineStatusReq;
import com.dh.im.service.user.model.req.PullUserOnlineStatusReq;
import com.dh.im.service.user.model.req.SetUserCustomerStatusReq;
import com.dh.im.service.user.model.req.SubscribeUserOnlineStatusReq;
import com.dh.im.service.user.model.resp.UserOnlineStatusResp;
import com.dh.im.service.user.service.ImUserStatusService;
import com.dh.im.service.utils.MessageProducer;
import com.dh.im.service.utils.UserSessionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImUserStatusServiceImpl implements ImUserStatusService {

    @Autowired
    private UserSessionUtils userSessionUtils;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private ImFriendShipService imFriendShipService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void processUserOnlineStatusNotify(UserStatusChangeNotifyContent content) {

        String userId = content.getUserId();
        Integer appId = content.getAppId();

        List<UserSession> userSession = userSessionUtils.getUserSession(appId, userId);
        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        BeanUtils.copyProperties(content, userStatusChangeNotifyPack);
        userStatusChangeNotifyPack.setClient(userSession);
        // 消息分发
        // 自己的同步端
        syncSender(userStatusChangeNotifyPack, userId, content);
        // 同步给好友和订阅了自己的人
        dispatcher(userStatusChangeNotifyPack, userId, appId, UserEventCommand.USER_ONLINE_STATUS_CHANGE_NOTIFY);

    }

    @Override
    public void subscribeUserOnlineStatus(SubscribeUserOnlineStatusReq req) {

        long subExpireTime = 0L;

        if (req != null && req.getSubTime() > 0) {
            subExpireTime = System.currentTimeMillis() + req.getSubTime();
        }
        for (String subUserId : Objects.requireNonNull(req).getSubUserId()) {
            String key = req.getAppId() + ":" + Constants.RedisConstants.subscribe + ":" + subUserId;
            stringRedisTemplate.opsForHash().put(key, req.getOperator(), subExpireTime);
        }

    }

    // 设置自定义状态
    // 记录并通知
    @Override
    public void setUserCustomerStatus(SetUserCustomerStatusReq req) {
        Integer customStatus = req.getCustomStatus();
        String customText = req.getCustomText();
        String userId = req.getUserId();
        Integer appId = req.getAppId();

        UserCustomStatusChangeNotifyPack userCustomStatusChangeNotifyPack = new UserCustomStatusChangeNotifyPack();
        userCustomStatusChangeNotifyPack.setUserId(userId);
        userCustomStatusChangeNotifyPack.setCustomStatus(customStatus);
        userCustomStatusChangeNotifyPack.setCustomText(customText);

        String key = appId + ":" + Constants.RedisConstants.userCustomerStatus + ":" + userId;
        stringRedisTemplate.opsForValue().set(key, JSONObject.toJSONString(userCustomStatusChangeNotifyPack));

        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setClientType(req.getClientType());
        clientInfo.setImei(req.getImei());
        clientInfo.setAppId(appId);
        // 多端同步
        syncSender(
                userCustomStatusChangeNotifyPack,
                userId,
                clientInfo
        );
        // 发送给好友和订阅者
        dispatcher(userCustomStatusChangeNotifyPack,
                userId,
                appId,
                UserEventCommand.USER_MODIFY
        );


    }

    @Override
    public Map<String, UserOnlineStatusResp> queryFriendOnlineStatus(PullFriendOnlineStatusReq req) {

        Integer appId = req.getAppId();
        String userId = req.getOperator();

        GetAllFriendShipReq beforeReq = new GetAllFriendShipReq();
        beforeReq.setFromId(userId);
        beforeReq.setAppId(appId);

        ResponseVO<List<ImFriendShipEntity>> resp = imFriendShipService.getAllFriendShip(beforeReq);
        if (resp.isOk()) {
            List<String> userIdList = resp.getData()
                    .stream()
                    .map(ImFriendShipEntity::getToId)
                    .collect(Collectors.toList());
            return getUserOnlineStatus(userIdList, appId);
        }
        return null;
    }

    @Override
    public Map<String, UserOnlineStatusResp> queryUserOnlineStatus(PullUserOnlineStatusReq req) {
        return getUserOnlineStatus(req.getUserList(), req.getAppId());
    }

    private Map<String, UserOnlineStatusResp> getUserOnlineStatus(List<String> userIds, Integer appId) {
        Map<String, UserOnlineStatusResp> result = new HashMap<>(userIds.size());

        userIds.forEach(userId -> {
            UserOnlineStatusResp resp = new UserOnlineStatusResp();
            List<UserSession> userSession = userSessionUtils.getUserSession(appId, userId);
            resp.setSession(userSession);
            String userKey = appId + ":" + Constants.RedisConstants.userCustomerStatus + userId;
            String s = stringRedisTemplate.opsForValue().get(userKey);
            if (StringUtils.isNotBlank(s)) {
                JSONObject parse = (JSONObject) JSONObject.parse(s);
                resp.setCustomText(parse.getString("customText"));
                resp.setCustomStatus(parse.getInteger("customStatus"));
            }
            result.put(userId, resp);
        });

        return result;
    }

    private void syncSender(Object pack, String userId, ClientInfo clientInfo) {
        messageProducer.sendToUserExceptClient(
                userId,
                UserEventCommand.USER_ONLINE_STATUS_CHANGE_NOTIFY_SYNC,
                pack,
                clientInfo
        );
    }

    private void dispatcher(Object pack, String userId, Integer appId, UserEventCommand command) {
        GetAllFriendShipReq req = new GetAllFriendShipReq();
        req.setFromId(userId);
        req.setAppId(appId);
        ResponseVO<List<ImFriendShipEntity>> resp = imFriendShipService.getAllFriendShip(req);
        if (resp.isOk()) {
            resp.getData()
                    .stream()
                    .map(ImFriendShipEntity::getToId)
                    .forEach(toId -> {
                        messageProducer.sendToUser(
                                toId,
                                command,
                                pack,
                                appId);
                    });
        }
        // 发送给临时订阅的人
        String key = appId + ":" + Constants.RedisConstants.subscribe + ":" + userId;
        Set<Object> subIds = stringRedisTemplate.opsForHash().keys(key);
        subIds.forEach(subId -> {
            long expireTime = (Long) Objects.requireNonNull(stringRedisTemplate.opsForHash().get(key, subId.toString()));
            if (expireTime > 0 && expireTime > System.currentTimeMillis()) {
                messageProducer.sendToUser(
                        subId.toString(),
                        UserEventCommand.USER_ONLINE_STATUS_CHANGE_NOTIFY,
                        pack,
                        appId
                );
            } else {
                // 过期删除
                stringRedisTemplate.opsForHash().delete(key, subId.toString());
            }
        });
    }

}
