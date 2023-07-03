package com.dh.im.common.enums.command;

public enum SystemCommand implements Command {

    // 心跳
    PING(0x270f),

    /**
     * 登陆：9000(0x2328)
     */
    LOGIN(0x2328),

    LOGINACK(0x2329),

    LOGOUT(0x232b),

    //下线通知 用于多端互斥  9002
    MUTUALLOGIN(0x232a),



    ;

    private int command;

    SystemCommand(int command) {
        this.command = command;
    }


    @Override
    public int getCommand() {
        return this.command;
    }
}
