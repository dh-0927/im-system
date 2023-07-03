package com.dh.im.service.utils;

import com.dh.im.common.ResponseVO;
import com.dh.im.common.config.AppConfig;
import com.dh.im.common.utils.HttpRequestUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class CallbackService {

    @Autowired
    private HttpRequestUtils httpRequestUtils;

    @Autowired
    private AppConfig appConfig;

    public void callback(Integer appId, String callbackCommand, String jsonBody) {

        try {
            httpRequestUtils.doPost(appConfig.getCallbackUrl(), Object.class, builderUrlParams(appId, callbackCommand),
                    jsonBody, null);
        } catch (Exception e) {
            log.error("callback 回调{} : {}出现异常 ： {} ",callbackCommand , appId, e.getMessage());
        }
    }

    public ResponseVO beforeCallback(Integer appId, String callbackCommand, String jsonBody) {

        try {
            ResponseVO responseVO = httpRequestUtils.doPost(appConfig.getCallbackUrl(), ResponseVO.class,
                    builderUrlParams(appId, callbackCommand), jsonBody, null);
            return responseVO;
        } catch (Exception e) {
            log.error("callback 之前 回调{} : {}出现异常 ： {} ",callbackCommand , appId, e.getMessage());
            return ResponseVO.errorResponse();
        }

    }

    public Map<String, Object> builderUrlParams(Integer appId, String command) {
        Map<String, Object> map = new HashMap<>();
        map.put("appId", appId);
        map.put("command", command);
        return map;
    }


}
