package com.mszlu.rpc.netty.codec;

import com.mszlu.rpc.compress.Compress;
import com.mszlu.rpc.constant.CompressTypeEnum;
import com.mszlu.rpc.constant.MessageTypeEnum;
import com.mszlu.rpc.constant.MsRpcConstants;
import com.mszlu.rpc.constant.SerializationTypeEnum;
import com.mszlu.rpc.exception.MsRpcException;
import com.mszlu.rpc.message.MsMessage;
import com.mszlu.rpc.message.MsRequest;
import com.mszlu.rpc.message.MsResponse;
import com.mszlu.rpc.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.util.ServiceLoader;

/**
 *   0     1     2     3     4        5     6     7     8         9          10      11     12  13  14   15 16
 *   +-----+-----+-----+-----+--------+----+----+----+------+-----------+-------+----- --+-----+-----+-------+
 *   |   magic   code        |version | full length         | messageType| codec|compress|    RequestId       |
 *   +-----------------------+--------+---------------------+-----------+-----------+-----------+------------+
 *   |                                                                                                       |
 *   |                                         body                                                          |
 *   |                                                                                                       |
 *   |                                        ... ...                                                        |
 *   +-------------------------------------------------------------------------------------------------------+
 * 4B  magic code（魔法数）   1B version（版本）   4B full length（消息长度）    1B messageType（消息类型）
 * 1B compress（压缩类型） 1B codec（序列化类型）    4B  requestId（请求的Id）
 * body（object类型数据）
 */
public class MsRpcDecoder extends LengthFieldBasedFrameDecoder {


    public MsRpcDecoder(){
        this(8 * 1024 * 1024,5,4,-9,0);
    }
    /**
     *
     * @param maxFrameLength 最大帧长度。它决定可以接收的数据的最大长度。如果超过，数据将被丢弃,根据实际环境定义
     * @param lengthFieldOffset 数据长度字段开始的偏移量, magic code+version=长度为5
     * @param lengthFieldLength 消息长度的大小  full length（消息长度） 长度为4
     * @param lengthAdjustment 补偿值 lengthAdjustment+数据长度取值=长度字段之后剩下包的字节数(x + 16=7 so x = -9)
     * @param initialBytesToStrip 忽略的字节长度，如果要接收所有的header+body 则为0，如果只接收body 则为header的长度 ,我们这为0
     */
    public MsRpcDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        Object decode = super.decode(ctx, in);
        // 数据发送过来后，先进入这里面，进行解码
        if (decode instanceof  ByteBuf){
            ByteBuf frame = (ByteBuf) decode;
            int length = frame.readableBytes();
            if (length < MsRpcConstants.TOTAL_LENGTH){
                throw new MsRpcException("数据长度不符");
            }
            return decodeFrame(frame);

        }
        return decode;
    }
    //1. 4B  magic code（魔法数）
    //2. 1B version（版本）
    //3. 4B full length（消息长度）
    //4. 1B messageType（消息类型）
    //5. 1B codec（序列化类型）
    //6. 1B compress（压缩类型）
    //7. 4B  requestId（请求的Id）
    //8. body（object类型数据）
    private Object decodeFrame(ByteBuf frame) {
        //按顺序进行读取
        //1. 检测魔法数
        checkMagicCode(frame);
        //2. 检查版本
        checkVersion(frame);
        //3.数据长度
        int fullLength = frame.readInt();
        //4.messageType 消息类型
        byte messageType = frame.readByte();
        //5. 1B codec（序列化类型）
        byte codec = frame.readByte();
        //6. 1B compress（压缩类型）
        byte compressType = frame.readByte();
        //4B  requestId（请求的Id）
        int requestId = frame.readInt();
        //获取数据长度
        int dataLength = fullLength - MsRpcConstants.TOTAL_LENGTH;
        MsMessage msMessage = MsMessage.builder()
                .messageType(messageType)
                .codec(codec)
                .compress(compressType)
                .requestId(requestId)
                .build();
        if (dataLength > 0){
            //有数据,读取body的数据
            byte[] bodyData = new byte[dataLength];
            frame.readBytes(bodyData);
            //在进行编码的时候，先序列化 后压缩
            //解压缩
            Compress compress = loadCompress(compressType);
            bodyData = compress.decompress(bodyData);
            //反序列化
            Serializer serializer = loadSerializer(codec);
            //根据不同的业务 进行反序列化
            //客户端 发请求  服务端 响应数据
            //MsRequest  MsResponse
            if (MessageTypeEnum.REQUEST.getCode() == messageType){
                MsRequest msRequest = (MsRequest) serializer.deserialize(bodyData, MsRequest.class);
                msMessage.setData(msRequest);
            }
            if (MessageTypeEnum.RESPONSE.getCode() == messageType){
                MsResponse msResponse = (MsResponse) serializer.deserialize(bodyData, MsResponse.class);
                msMessage.setData(msResponse);
            }

        }
        return msMessage;
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

    private void checkVersion(ByteBuf frame) {
        byte b = frame.readByte();
        if (b != MsRpcConstants.VERSION){
            throw new MsRpcException("未知的version");
        }
    }

    private void checkMagicCode(ByteBuf frame) {
        int length = MsRpcConstants.MAGIC_NUMBER.length;
        byte[] tmp = new byte[length];
        frame.readBytes(tmp);
        for (int i = 0;i< length; i++){
            if (tmp[i] != MsRpcConstants.MAGIC_NUMBER[i]){
                throw new MsRpcException("传递魔法数有误");
            }
        }

    }
}
