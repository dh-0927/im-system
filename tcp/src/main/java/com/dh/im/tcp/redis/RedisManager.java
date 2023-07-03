package com.dh.im.tcp.redis;

import com.dh.im.codec.config.BootstrapConfig;
import com.dh.im.tcp.receiver.UserLoginMessageListener;
import org.redisson.api.RedissonClient;

public class RedisManager {

    private static RedissonClient redissonClient;

    private static Integer loginModel;

    public static void init(BootstrapConfig config) {
        SingleClientStrategy singleClientStrategy = new SingleClientStrategy();
        redissonClient = singleClientStrategy.getRedissonClient(config.getIm().getRedis());

        // 初始化监听器
        loginModel = config.getIm().getLoginModel();
        UserLoginMessageListener userLoginMessageListener = new UserLoginMessageListener(loginModel);
        userLoginMessageListener.listenerUserLogin();
    }

    public static RedissonClient getRedissonClient() {
        return redissonClient;
    }

}
