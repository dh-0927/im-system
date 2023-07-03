package com.dh.im.service.user.model.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportUserResp {
    private List<String> successId;
    private List<String> errorId;
}
