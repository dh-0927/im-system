package com.dh.im.service.group.model.req;

import com.dh.im.common.model.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class ImportGroupMemberReq extends RequestBase {

    @NotBlank(message = "群id不能未空")
    private String groupId;

    private List<GroupMemberDto> members;
}
