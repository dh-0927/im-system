package com.dh.im.codec.park.message;

import lombok.Data;

@Data
public class MessageReadedPack {

    private long messageSequence;

    private String fromId;

    private String groupId;

    private String toId;

    private Integer conversationType;
}
