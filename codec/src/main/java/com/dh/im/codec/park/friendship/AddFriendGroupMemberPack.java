package com.dh.im.codec.park.friendship;

import lombok.Data;

import java.util.List;


@Data
public class AddFriendGroupMemberPack {

    public String fromId;

    private String groupName;

    private List<String> toIds;

    /** 序列号*/
    private Long sequence;
}
