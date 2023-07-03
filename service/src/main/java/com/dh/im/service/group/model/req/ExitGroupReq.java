package com.dh.im.service.group.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ExitGroupReq extends RequestBase {

    @NotBlank(message = "群id不能为空")
    private String groupId;
}
