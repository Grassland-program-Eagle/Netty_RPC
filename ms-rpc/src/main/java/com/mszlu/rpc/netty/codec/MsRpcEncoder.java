package com.mszlu.rpc.netty.codec;

import com.mszlu.rpc.compress.Compress;
import com.mszlu.rpc.constant.CompressTypeEnum;
import com.mszlu.rpc.constant.MsRpcConstants;
import com.mszlu.rpc.constant.SerializationTypeEnum;
import com.mszlu.rpc.exception.MsRpcException;
import com.mszlu.rpc.message.MsMessage;
import com.mszlu.rpc.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

public class MsRpcEncoder extends MessageToByteEncoder<MsMessage> {

    private static final AtomicInteger ATOMIC_INTEGER = new AtomicInteger(0);

    //1. 4B  magic code（魔法数）
    //2. 1B version（版本）
    //3. 4B full length（消息长度）
    //4. 1B messageType（消息类型）
    //5. 1B codec（序列化类型）
    //6. 1B compress（压缩类型）
    //7. 4B  requestId（请求的Id）
    //8. body（object类型数据）
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext,
                          MsMessage msMessage,
                          ByteBuf out) throws Exception {
        //拿到message 要进行编码处理
        out.writeBytes(MsRpcConstants.MAGIC_NUMBER);
        out.writeByte(MsRpcConstants.VERSION);
        // 预留数据长度位置
        out.writerIndex(out.writerIndex() + 4);
        out.writeByte(msMessage.getMessageType());
        //序列化 先进行序列化 在进行压缩
        out.writeByte(msMessage.getCodec());
        out.writeByte(msMessage.getCompress());
        out.writeInt(ATOMIC_INTEGER.getAndIncrement());
        Object data = msMessage.getData();
        //header 长度为 16
        int fullLength = MsRpcConstants.HEAD_LENGTH;
        //序列化
        Serializer serializer = loadSerializer(msMessage.getCodec());
        byte[] bodyBytes = serializer.serialize(data);
//
        Compress compress = loadCompress(msMessage.getCompress());
        bodyBytes = compress.compress(bodyBytes);
        fullLength += bodyBytes.length;
        out.writeBytes(bodyBytes);
        int writerIndex = out.writerIndex();
        //将fullLength写入之前的预留的位置
        out.writerIndex(writerIndex - fullLength + MsRpcConstants.MAGIC_NUMBER.length + 1);
        out.writeInt(fullLength);
        out.writerIndex(writerIndex);
    }

    private Serializer loadSerializer(byte codec) {
        String name = SerializationTypeEnum.getName(codec);
        ServiceLoader<Serializer> load = ServiceLoader.load(Serializer.class);
        for (Serializer serializer : load){
            if (serializer.name().equals(name)){
                return serializer;
            }
        }
        throw new MsRpcException("无对应的序列化类型");
    }

    private Compress loadCompress(byte compressType) {
        String name = CompressTypeEnum.getName(compressType);
        ServiceLoader<Compress> load = ServiceLoader.load(Compress.class);
        for (Compress compress : load){
            if (compress.name().equals(name)){
                return compress;
            }
        }
        throw new MsRpcException("无对应的压缩类型");
    }
}
