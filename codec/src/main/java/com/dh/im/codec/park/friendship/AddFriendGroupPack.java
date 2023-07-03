package com.dh.im.codec.park.friendship;

import lombok.Data;


@Data
public class AddFriendGroupPack {
    public String fromId;

    private String groupName;

    /** 序列号*/
    private Long sequence;
}
