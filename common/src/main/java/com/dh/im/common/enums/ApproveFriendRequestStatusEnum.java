package com.dh.im.common.enums;

public enum ApproveFriendRequestStatusEnum {

    /**
     * 1 同意；2 拒绝。
     */
    AGREE(1),

    REJECT(2),
    ;

    private int code;

    ApproveFriendRequestStatusEnum(int code){
        this.code=code;
    }

    public int getCode() {
        return code;
    }
}
