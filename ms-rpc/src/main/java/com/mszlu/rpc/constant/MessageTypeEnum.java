package com.mszlu.rpc.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MessageTypeEnum {

    REQUEST((byte) 0x01, "request"),
    RESPONSE((byte) 0x02, "response"),
    HEARTBEAT_PING((byte) 0x03, "heart ping"),
    HEARTBEAT_PONG((byte) 0x04, "heart pong");

    private final byte code;
    private final String name;

    public static String getName(byte code) {
        for (MessageTypeEnum c : MessageTypeEnum.values()) {
            if (c.getCode() == code) {
                return c.name;
            }
        }
        return null;
    }

}
