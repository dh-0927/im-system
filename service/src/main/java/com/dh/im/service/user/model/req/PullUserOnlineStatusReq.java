package com.dh.im.service.user.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

import java.util.List;

@Data
public class PullUserOnlineStatusReq extends RequestBase {

    private List<String> userList;

}
