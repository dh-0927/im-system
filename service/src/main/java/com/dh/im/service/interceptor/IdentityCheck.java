package com.dh.im.service.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.dh.im.common.BaseErrorCode;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.constant.Constants;
import com.dh.im.common.enums.GateWayErrorCode;
import com.dh.im.common.exception.ApplicationExceptionEnum;
import com.dh.im.common.utils.SigAPI;
import com.dh.im.service.user.service.ImUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class IdentityCheck {

    @Autowired
    private ImUserService imUserService;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public ApplicationExceptionEnum checkUserSign(String identifier, String appId, String userSign) {

        String key = appId + ":" + Constants.RedisConstants.userSign + ":" + identifier + userSign;

        String cacheUser = stringRedisTemplate.opsForValue().get(key);
        if (StringUtils.isNotBlank(cacheUser) &&
                Long.parseLong(cacheUser) > System.currentTimeMillis() / 1000) {
            return BaseErrorCode.SUCCESS;
        }

        // 调用sigApi对userSign进行解密
        JSONObject jsonObject = SigAPI.decodeUserSig(userSign);

        // 取出解密后的appId和操作人和过期时间做匹配，不通过则提示错误
        long expireTime = 0L;
        long expireSec = 0L;
        String decoderAppId = "";
        String decoderIdentifier = "";

        try {
            decoderAppId = jsonObject.getString("TLS.appId");
            decoderIdentifier = jsonObject.getString("TLS.identifier");
            String expire = jsonObject.getString("TLS.expire");
            String expireTimeStr = jsonObject.getString("TLS.expireTime");
            expireSec = Long.parseLong(expire);
            expireTime = Long.parseLong(expireTimeStr) + expireSec;

        } catch (Exception e) {
            e.printStackTrace();
            log.error("checkUserSig-error: {}", e.getMessage());
        }

        if (!decoderIdentifier.equals(identifier)) {
            return GateWayErrorCode.USERSIGN_OPERATE_NOT_MATE;
        }

        if (!decoderAppId.equals(appId)) {
            return GateWayErrorCode.USERSIGN_IS_ERROR;
        }

        if (expireSec == 0L) {
            return GateWayErrorCode.USERSIGN_IS_EXPIRED;
        }

        if (expireTime < System.currentTimeMillis() / 1000) {
            return GateWayErrorCode.USERSIGN_IS_EXPIRED;
        }

        // key： appId + "xxx" + userId + sign

        long eTime = expireTime - System.currentTimeMillis() / 1000;
        stringRedisTemplate.opsForValue()
                .set(key, Long.toString(expireTime), eTime, TimeUnit.SECONDS);

        return BaseErrorCode.SUCCESS;
    }

}
