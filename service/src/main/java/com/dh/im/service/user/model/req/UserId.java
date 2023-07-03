package com.dh.im.service.user.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class UserId extends RequestBase {

    @NotNull
    private String userId;

}
