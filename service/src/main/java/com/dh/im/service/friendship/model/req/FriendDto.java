package com.dh.im.service.friendship.model.req;

import lombok.Data;

import javax.validation.constraints.NotBlank;


@Data
public class FriendDto {

    @NotBlank(message = "toId不能为空")
    private String toId;

    private String remark;

    private String addSource;

    private String extra;

    private String addWording;
}
