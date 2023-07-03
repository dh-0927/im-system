package com.dh.im.common.model.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class CheckSendMessageReq implements Serializable {

    private String fromId;

    private String toId;

    private Integer appId;

    private Integer command;

}
