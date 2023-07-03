package com.dh.im.codec.park.user;


import lombok.Data;

@Data
public class UserCustomStatusChangeNotifyPack {

    private String customText;

    private Integer customStatus;

    private String userId;

}
