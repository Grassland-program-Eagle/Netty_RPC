package com.mszlu.rpc.netty.handler;

import com.mszlu.rpc.constant.MessageTypeEnum;
import com.mszlu.rpc.constant.MsRpcConstants;
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
public class MsNettyServerHandler extends ChannelInboundHandlerAdapter {

    private MsRequestHandler msRequestHandler;

    public MsNettyServerHandler(){
        msRequestHandler = SingletonFactory.getInstance(MsRequestHandler.class);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //接收客户端发来的数据，数据肯定包括了 要调用的服务提供者的 接口，方法，
        //解析消息，去找到对应的服务提供者，然后调用，得到调用结果，发消息给客户端就可以了
        try {
            if (msg instanceof MsMessage){
                // 拿到请求数据 ，调用对应服务提供方方法 获取结果 给客户端返回
                MsMessage msMessage = (MsMessage) msg;
                byte messageType = msMessage.getMessageType();
                if (MessageTypeEnum.HEARTBEAT_PING.getCode() == messageType){
                    msMessage.setMessageType(MessageTypeEnum.HEARTBEAT_PONG.getCode());
                    msMessage.setData(MsRpcConstants.HEART_PONG);
                }
                if (MessageTypeEnum.REQUEST.getCode() == messageType){
                    MsRequest msRequest = (MsRequest) msMessage.getData();
                    //处理业务，使用反射找到方法 发起调用 获取结果
                    Object result = msRequestHandler.handler(msRequest);
                    msMessage.setMessageType(MessageTypeEnum.RESPONSE.getCode());
                    if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                        MsResponse msResponse = MsResponse.success(result, msRequest.getRequestId());
                        msMessage.setData(msResponse);
                    }else{
                        msMessage.setData(MsResponse.fail("net fail"));
                    }
                }
                ctx.writeAndFlush(msMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        }catch (Exception e){
            log.error("读取消息出错:",e);
        }finally {
            //释放 以防内存泄露
            ReferenceCountUtil.release(msg);
        }

    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent){
            IdleStateEvent stateEvent = (IdleStateEvent) evt;
            IdleState state = stateEvent.state();
            if (state == IdleState.READER_IDLE){
                log.info("收到了心跳检测，超时未读取....");
//                ctx.close();
            }else {
                super.userEventTriggered(ctx,evt);
            }

        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        ctx.close();
    }
}
