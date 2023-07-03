package com.dh.im.service.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.dh.im.common.BaseErrorCode;
import com.dh.im.common.ResponseVO;
import com.dh.im.common.enums.GateWayErrorCode;
import com.dh.im.common.exception.ApplicationExceptionEnum;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Component
public class GateWayInterceptor implements HandlerInterceptor {


    @Autowired
    private IdentityCheck identityCheck;

    @Override
    public boolean preHandle(HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws Exception {
        if (true) {
            return true;
        }

        String appId = request.getParameter("appId");
        if (StringUtils.isBlank(appId)) {
            resp(ResponseVO.errorResponse(GateWayErrorCode.APPID_NOT_EXIST), response);
            return false;
        }

        String identifier = request.getParameter("identifier");
        if (StringUtils.isBlank(identifier)) {
            resp(ResponseVO.errorResponse(GateWayErrorCode.OPERATER_NOT_EXIST), response);
            return false;
        }

        String userSign = request.getParameter("userSign");
        if (StringUtils.isBlank(userSign)) {
            resp(ResponseVO.errorResponse(GateWayErrorCode.USERSIGN_NOT_EXIST), response);
            return false;
        }

        // 校验签名和操作人和appId是否匹配
        ApplicationExceptionEnum applicationExceptionEnum =
                identityCheck.checkUserSign(identifier, appId, userSign);

        if (applicationExceptionEnum != BaseErrorCode.SUCCESS) {
            resp(ResponseVO.errorResponse(applicationExceptionEnum), response);
            return false;
        }

        return true;
    }

    private void resp(ResponseVO respVO, HttpServletResponse response) {
        PrintWriter writer = null;
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=utf-8");

        try {
            String resp = JSONObject.toJSONString(respVO);
            writer = response.getWriter();
            writer.write(resp);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

    }
}
