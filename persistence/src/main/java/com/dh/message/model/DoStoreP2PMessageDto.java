package com.dh.message.model;

import com.dh.im.common.model.message.MessageContent;
import com.dh.message.dao.ImMessageBodyEntity;
import lombok.Data;

@Data
public class DoStoreP2PMessageDto {

    private MessageContent messageContent;

    private ImMessageBodyEntity imMessageBodyEntity;

}
