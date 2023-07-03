package com.dh.message.model;

import com.dh.im.common.model.message.GroupChatMessageContent;
import com.dh.im.common.model.message.MessageContent;
import com.dh.message.dao.ImMessageBodyEntity;
import lombok.Data;

@Data
public class DoStoreGroupMessageDto {

    private GroupChatMessageContent groupChatMessageContent;

    private ImMessageBodyEntity imMessageBodyEntity;

}
