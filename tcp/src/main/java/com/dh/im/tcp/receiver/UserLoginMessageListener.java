package com.dh.im.tcp.receiver;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.proto.MessagePack;
import com.dh.im.common.ClientType;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.DeviceMultiLoginEnum;
import com.dh.im.common.enums.command.SystemCommand;
import com.dh.im.common.model.UserClientDto;
import com.dh.im.tcp.redis.RedisManager;
import com.dh.im.tcp.utils.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;

import java.util.List;

/**
 * # 1 只允许一端在线，手机/电脑/web 踢掉除了本client+imel的设备
 * # 2 允许手机/电脑的一台设备 + web在线 踢掉除了本client+imel的非web端设备
 * # 3 允许手机和电脑单设备 + web 同时在线 踢掉非本client+imel的同端设备
 * # 4 允许所有端多设备登录 不踢任何设备
 */

@Slf4j
public class UserLoginMessageListener {

    private Integer loginModel;

    public UserLoginMessageListener(Integer loginModel) {
        this.loginModel = loginModel;
    }

    public void listenerUserLogin() {
        RTopic topic = RedisManager.getRedissonClient().getTopic(Constants.RedisConstants.UserLoginChannel);
        topic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence channel, String msg) {
                log.info("收到用户上线：{}", msg);
                // 当前登陆的用户
                UserClientDto userClientDto = JSONObject.parseObject(msg, UserClientDto.class);
                Integer nowLoginClientType = userClientDto.getClientType();
                String nowLoginImei = userClientDto.getImei();
                String NowLoginClient = nowLoginClientType + nowLoginImei;

                // 取出当前服务器中当前登陆用户的所有channel
                List<NioSocketChannel> nioSocketChannels =
                        SessionSocketHolder.get(userClientDto.getAppId(), userClientDto.getUserId());

                nioSocketChannels.forEach(nioSocketChannel -> {

                    Integer alreadyLoginClientType = (Integer)
                            nioSocketChannel.attr(AttributeKey.valueOf(Constants.ClientType)).get();
                    String alreadyLoginImei = (String)
                            nioSocketChannel.attr(AttributeKey.valueOf(Constants.Imei)).get();

                    String alreadyLoginClient = alreadyLoginClientType + alreadyLoginImei;


                    if (loginModel == DeviceMultiLoginEnum.ONE.getLoginMode()) {
                        // 当前登陆标识

                        // 不相等，说明有其他客户端已登陆，踢掉已登陆的客户端
                        if (!alreadyLoginClient.equals(NowLoginClient)) {
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }

                    } else if (loginModel == DeviceMultiLoginEnum.TWO.getLoginMode()) {
                        if (nowLoginClientType == ClientType.WEB.getCode()) {
                            return;
                        }
                        if (alreadyLoginClientType == ClientType.WEB.getCode()) {
                            return;
                        }
                        if (!alreadyLoginClient.equals(NowLoginClient)) {
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }

                    } else if (loginModel == DeviceMultiLoginEnum.THREE.getLoginMode()) {
                        if (nowLoginClientType == ClientType.WEB.getCode()) {
                            return;
                        }
                        if (alreadyLoginClientType == ClientType.WEB.getCode()) {
                            return;
                        }

                        boolean isSameClient = false;
                        if ((nowLoginClientType == ClientType.IOS.getCode() ||
                                nowLoginClientType == ClientType.ANDROID.getCode()) &&
                                (alreadyLoginClientType == ClientType.IOS.getCode() ||
                                        alreadyLoginClientType == ClientType.ANDROID.getCode())) {
                            isSameClient = true;
                        }

                        if ((nowLoginClientType == ClientType.WINDOWS.getCode() ||
                                nowLoginClientType == ClientType.MAC.getCode()) &&
                                (alreadyLoginClientType == ClientType.WINDOWS.getCode() ||
                                        alreadyLoginClientType == ClientType.MAC.getCode())) {
                            isSameClient = true;
                        }

                        if (isSameClient && !alreadyLoginClient.equals(NowLoginClient)) {
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(Constants.UserId)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }

                    }


                });

                if (loginModel == DeviceMultiLoginEnum.ONE.getLoginMode()) {

                    String clientImei = nowLoginClientType + nowLoginImei;


                } else if (loginModel == DeviceMultiLoginEnum.TWO.getLoginMode()) {

                } else if (loginModel == DeviceMultiLoginEnum.THREE.getLoginMode()) {

                }
            }
        });

    }
}
