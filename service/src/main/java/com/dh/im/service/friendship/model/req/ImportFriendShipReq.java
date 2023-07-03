package com.dh.im.service.friendship.model.req;

import com.dh.im.common.enums.FriendShipStatusEnum;
import com.dh.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class ImportFriendShipReq extends RequestBase {

    @NotBlank(message = "fromId不能为空")
    private String fromId;

    private List<ImportFriendDto> friendItem;

    @Data
    public static class ImportFriendDto {
        // toId
        private String toId;
        // 备注
        private String remark;
        // 来源
        private String addSource;
        // 状态
        private Integer status = FriendShipStatusEnum.FRIEND_STATUS_NO_FRIEND.getCode();
        // 是否拉黑
        private Integer black = FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode();
    }
}
