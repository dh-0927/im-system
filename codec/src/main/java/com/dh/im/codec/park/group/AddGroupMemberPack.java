package com.dh.im.codec.park.group;

import lombok.Data;

import java.util.List;


@Data
public class AddGroupMemberPack {

    private String groupId;

    private List<String> members;

}
