package com.dh.im.service.group.model.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberResp {

    // 成员id
    private String memberId;

    // 加入结果 0：成功 1：失败 2：已经是群成员
    private Integer result;

    private String resultMessage;

    public static AddMemberResp success(String memberId) {
        return new AddMemberResp(memberId, 0, "加入群成功");
    }

    public static AddMemberResp error(String memberId, String resultMessage) {
        return new AddMemberResp(memberId, 1, resultMessage);
    }

    public static AddMemberResp exists(String memberId) {
        return new AddMemberResp(memberId, 2, "已经是群成员");
    }


}
