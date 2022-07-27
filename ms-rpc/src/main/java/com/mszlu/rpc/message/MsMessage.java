package com.mszlu.rpc.message;


import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class MsMessage {

    //rpc message type
    private byte messageType;
    //serialization type
    private byte codec;
    //compress type
    private byte compress;
    //request id
    private int requestId;
    //request data
    private Object data;

}
