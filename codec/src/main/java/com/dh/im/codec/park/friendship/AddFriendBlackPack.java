package com.dh.im.codec.park.friendship;

import lombok.Data;


@Data
public class AddFriendBlackPack {
    private String fromId;

    private String toId;

    private Long sequence;
}
