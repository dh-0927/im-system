package com.dh.im.service.friendship.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@Data
public class AddFriendReq extends RequestBase {

    @NotBlank(message = "fromId不能为空")
    private String fromId;

    @Valid
    private FriendDto toItem;

}
