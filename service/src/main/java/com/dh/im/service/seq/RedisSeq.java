package com.dh.im.service.seq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RedisSeq {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public long doGetSeq(String key) {
        return Objects.requireNonNull(stringRedisTemplate.opsForValue().increment(key));
    }
}
