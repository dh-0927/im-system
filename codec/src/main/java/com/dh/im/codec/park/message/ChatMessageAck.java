package com.dh.im.codec.park.message;

import lombok.Data;

@Data
public class ChatMessageAck {

    private String messageId;

    private Long messageSequence;

    public ChatMessageAck(String messageId, Long messageSequence) {
        this.messageId = messageId;
        this.messageSequence = messageSequence;
    }
    public ChatMessageAck(String messageId) {
        this.messageId = messageId;
    }
}
