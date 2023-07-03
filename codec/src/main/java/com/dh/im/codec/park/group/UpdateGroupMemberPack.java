package com.dh.im.codec.park.group;

import lombok.Data;


@Data
public class UpdateGroupMemberPack {

    private String groupId;

    private String memberId;

    private String alias;

    private String extra;
}
