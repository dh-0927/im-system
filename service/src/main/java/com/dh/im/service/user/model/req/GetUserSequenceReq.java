package com.dh.im.service.user.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

@Data
public class GetUserSequenceReq extends RequestBase {

    private String userId;

}
