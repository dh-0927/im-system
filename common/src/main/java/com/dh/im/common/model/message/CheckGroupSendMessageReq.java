package com.dh.im.common.model.message;

import lombok.Data;

import java.io.Serializable;

@Data
public class CheckGroupSendMessageReq implements Serializable {

    private String fromId;

    private String groupId;

    private Integer appId;

    private Integer command;

}
