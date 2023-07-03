package com.dh.im.service.friendship.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class CheckFriendShipReq extends RequestBase {

    @NotBlank(message = "fromId不能为空")
    private String fromId;

    @NotNull(message = "toIds不能为空")
    private List<String> toIds;

    @NotNull(message = "checkType不能为空")
    private Integer checkType;
}
