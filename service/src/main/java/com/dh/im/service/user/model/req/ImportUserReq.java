package com.dh.im.service.user.model.req;

import com.dh.im.common.model.RequestBase;
import com.dh.im.service.user.dao.ImUserDataEntity;
import lombok.Data;

import java.util.List;

@Data
public class ImportUserReq extends RequestBase {

    private List<ImUserDataEntity> userData;

}
