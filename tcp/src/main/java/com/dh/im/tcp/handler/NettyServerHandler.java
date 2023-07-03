package com.dh.im.tcp.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.dh.im.codec.park.LoginPark;
import com.dh.im.codec.park.message.ChatMessageAck;
import com.dh.im.codec.park.user.LoginAckPack;
import com.dh.im.codec.park.user.UserStatusChangeNotifyPack;
import com.dh.im.codec.proto.Message;
import com.dh.im.codec.proto.MessageHeader;
import com.dh.im.codec.proto.MessagePack;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.ImConnectStatusEnum;
import com.dh.im.common.enums.command.GroupEventCommand;
import com.dh.im.common.enums.command.MessageCommand;
import com.dh.im.common.enums.command.SystemCommand;
import com.dh.im.common.enums.command.UserEventCommand;
import com.dh.im.common.model.UserClientDto;
import com.dh.im.common.model.UserSession;
import com.dh.im.common.model.message.CheckGroupSendMessageReq;
import com.dh.im.common.model.message.CheckSendMessageReq;
import com.dh.im.tcp.feign.FeignMessageService;
import com.dh.im.tcp.publish.MqMessageProducer;
import com.dh.im.tcp.redis.RedisManager;
import com.dh.im.tcp.utils.SessionSocketHolder;
import feign.Feign;
import feign.Request;

import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;


import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import static com.dh.im.common.constant.Constants.RedisConstants.UserLoginChannel;

public class NettyServerHandler extends SimpleChannelInboundHandler<Message> {

    private Integer brokerId;

    private FeignMessageService feignMessageService;

    public NettyServerHandler(Integer brokerId, String logicUrl) {
        this.brokerId = brokerId;

        feignMessageService = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .options(new Request.Options(1000, TimeUnit.MILLISECONDS, 2000, TimeUnit.MILLISECONDS, false))
                .target(FeignMessageService.class, logicUrl);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {

        MessageHeader messageHeader = msg.getMessageHeader();

        Channel channel = ctx.channel();

        Integer command = messageHeader.getCommand();

        // 登陆指令
        if (command == SystemCommand.LOGIN.getCommand()) {

            LoginPark loginPark = JSON.parseObject(JSONObject.toJSONString(msg.getMessagePack()),
                    new TypeReference<LoginPark>() {
                    }.getType());

            String userId = loginPark.getUserId();
            Integer appId = messageHeader.getAppId();
            Integer clientType = messageHeader.getClientType();
            String imei = messageHeader.getImei();

            channel.attr(AttributeKey.valueOf(Constants.UserId)).set(userId);
            channel.attr(AttributeKey.valueOf(Constants.AppId)).set(appId);
            channel.attr(AttributeKey.valueOf(Constants.ClientType)).set(clientType);
            channel.attr(AttributeKey.valueOf(Constants.Imei)).set(imei);

            // 将channel存起来
            UserSession userSession = new UserSession();
            userSession.setAppId(appId);
            userSession.setClientType(clientType);
            userSession.setVersion(messageHeader.getVersion());
            userSession.setUserId(userId);
            userSession.setConnectState(ImConnectStatusEnum.ONLINE_STATUS.getCode());
            userSession.setBrokerId(brokerId);
            userSession.setImei(imei);

            try {
                InetAddress localHost = InetAddress.getLocalHost();
                userSession.setBrokerHost(localHost.getHostAddress());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

            // 存到redis
            RedissonClient redissonClient = RedisManager.getRedissonClient();
            RMap<String, String> map = redissonClient.getMap
                    (appId + Constants.RedisConstants.UserSessionConstants + userId);

            map.put(clientType + ":" + imei, JSONObject.toJSONString(userSession));

            SessionSocketHolder.put(appId, clientType, imei, userId, (NioSocketChannel) channel);

            UserClientDto dto = new UserClientDto();
            dto.setImei(imei);
            dto.setUserId(userId);
            dto.setAppId(appId);
            dto.setClientType(clientType);

            // 将客户端上线消息以广播模式发布给所有服务器
            RTopic topic = redissonClient.getTopic(Constants.RedisConstants.UserLoginChannel);
            topic.publish(JSONObject.toJSONString(dto));

            // 将状态变更通知给逻辑层
            UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
            userStatusChangeNotifyPack.setAppId(appId);
            userStatusChangeNotifyPack.setUserId(userId);
            userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.ONLINE_STATUS.getCode());

            MqMessageProducer.sendMessage(
                    userStatusChangeNotifyPack,
                    messageHeader,
                    UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());

            // 告诉用户登陆成功回复 ack
            MessagePack<LoginAckPack> loginSuccess = new MessagePack<>();
            LoginAckPack loginAckPack = new LoginAckPack();
            loginAckPack.setUserId(userId);
            loginSuccess.setCommand(SystemCommand.LOGINACK.getCommand());
            loginSuccess.setData(loginAckPack);
            loginSuccess.setImei(imei);
            loginSuccess.setAppId(appId);

            channel.writeAndFlush(loginSuccess);

        } else if (command == SystemCommand.LOGOUT.getCommand()) {
            // 退出登陆
            SessionSocketHolder.removeUserSession((NioSocketChannel) channel);

        } else if (command == SystemCommand.PING.getCommand()) {
            channel.attr(AttributeKey.valueOf(Constants.ReadTime)).set(System.currentTimeMillis());
        } else if (command == MessageCommand.MSG_P2P.getCommand() ||
                command == GroupEventCommand.MSG_GROUP.getCommand()) {

            try {
                ResponseVO responseVO = null;

                JSONObject jsonObject = JSON.parseObject(JSONObject.toJSONString(msg.getMessagePack()));
                if (command == MessageCommand.MSG_P2P.getCommand()) {
                    CheckSendMessageReq req = new CheckSendMessageReq();
                    req.setAppId(msg.getMessageHeader().getAppId());
                    req.setCommand(msg.getMessageHeader().getCommand());
                    req.setFromId(jsonObject.getString("fromId"));
                    String toId = jsonObject.getString("toId");
                    req.setToId(toId);
                    responseVO = feignMessageService.checkSendMessage(req);
                } else {
                    CheckGroupSendMessageReq req = new CheckGroupSendMessageReq();
                    req.setAppId(msg.getMessageHeader().getAppId());
                    req.setCommand(msg.getMessageHeader().getCommand());
                    req.setFromId(jsonObject.getString("fromId"));
                    String groupId = jsonObject.getString("groupId");
                    req.setGroupId(groupId);
                    responseVO = feignMessageService.checkGroupSendMessage(req);
                }

                // 调用校验消息发送方的接口
                // 成功投递到ack
                if (responseVO.isOk()) {
                    MqMessageProducer.sendMessage(msg, command);
                } else {
                    Integer ackCommand = 0;
                    if (command == MessageCommand.MSG_P2P.getCommand()) {
                        ackCommand = MessageCommand.MSG_P2P.getCommand();
                    } else {
                        ackCommand = GroupEventCommand.GROUP_MSG_ACK.getCommand();
                    }

                    ChatMessageAck chatMessageAck = new ChatMessageAck(jsonObject.getString("messageId"));
                    responseVO.setData(chatMessageAck);
                    MessagePack<ResponseVO> ack = new MessagePack<>();
                    ack.setData(responseVO);
                    ack.setCommand(ackCommand);

                    ctx.channel().writeAndFlush(ack);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            MqMessageProducer.sendMessage(msg, command);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 设置离线
        SessionSocketHolder.offlineUserSession(((NioSocketChannel) ctx.channel()));
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
    }
}
