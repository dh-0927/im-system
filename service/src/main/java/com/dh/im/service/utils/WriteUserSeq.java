package com.dh.im.service.utils;

import com.dh.im.common.constant.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class WriteUserSeq {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public void writeUserSeq(Integer appId, String userId, String type, Long seq) {

        String key = appId + ":" + Constants.RedisConstants.SeqPrefix + ":" + userId;
        stringRedisTemplate.opsForHash().put(key, type, seq.toString());

    }

}
