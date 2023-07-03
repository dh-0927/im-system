package com.dh.im.tcp.feign;

import com.dh.im.common.ResponseVO;

import com.dh.im.common.model.message.CheckGroupSendMessageReq;
import com.dh.im.common.model.message.CheckSendMessageReq;
import feign.Headers;
import feign.RequestLine;


public interface FeignMessageService {

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @RequestLine("POST /message/checkSend")
    ResponseVO checkSendMessage(CheckSendMessageReq req);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @RequestLine("POST /group/checkGroupSend")
    ResponseVO checkGroupSendMessage(CheckGroupSendMessageReq req);

}
