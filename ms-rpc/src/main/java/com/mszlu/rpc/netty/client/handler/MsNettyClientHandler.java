package com.mszlu.rpc.netty.client.handler;

import com.mszlu.rpc.constant.CompressTypeEnum;
import com.mszlu.rpc.constant.MessageTypeEnum;
import com.mszlu.rpc.constant.MsRpcConstants;
import com.mszlu.rpc.constant.SerializationTypeEnum;
import com.mszlu.rpc.factory.SingletonFactory;
import com.mszlu.rpc.message.MsMessage;
import com.mszlu.rpc.message.MsRequest;
import com.mszlu.rpc.message.MsResponse;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MsNettyClientHandler extends ChannelInboundHandlerAdapter {
    private UnprocessedRequests unprocessedRequests;

    public MsNettyClientHandler(){
        unprocessedRequests = SingletonFactory.getInstance(UnprocessedRequests.class);
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            //一旦客户端发出消息，在这就得等待接收
            if (msg instanceof MsMessage) {
                MsMessage msMessage = (MsMessage) msg;
                Object data = msMessage.getData();
                if (MessageTypeEnum.RESPONSE.getCode() == msMessage.getMessageType()) {
                    MsResponse msResponse = (MsResponse) data;
                    unprocessedRequests.complete(msResponse);
                }
                //
            }
        }catch (Exception e){
            log.error("客户端读取消息出错:",e);
        }finally {
            //释放 以防内存泄露
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
       if (evt instanceof IdleStateEvent){
           IdleStateEvent stateEvent = (IdleStateEvent) evt;
           if (stateEvent.state() == IdleState.WRITER_IDLE){
               log.info("客户端发送了心跳包...");
               //进行心跳检测，发送一个心跳包去服务端
               MsMessage msMessage = MsMessage.builder()
                       .messageType(MessageTypeEnum.HEARTBEAT_PING.getCode())
                       .compress(CompressTypeEnum.GZIP.getCode())
                       .codec(SerializationTypeEnum.PROTO_STUFF.getCode())
                       .data(MsRpcConstants.HEART_PING)
                       .build();
               ctx.channel().writeAndFlush(msMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
           }
       }else {
           super.userEventTriggered(ctx,evt);
       }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.info("客户端连接上了...连接正常");
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //如果触发了这个方法 代表服务端 关闭连接了
        super.channelInactive(ctx);
        log.info("服务端关闭了连接....");
        //清除对应的缓存

        ctx.fireChannelInactive();
    }
}
