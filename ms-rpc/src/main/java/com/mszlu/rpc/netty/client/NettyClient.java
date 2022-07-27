package com.mszlu.rpc.netty.client;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.RandomUtils;
import com.mszlu.rpc.config.MsRpcConfig;
import com.mszlu.rpc.constant.CompressTypeEnum;
import com.mszlu.rpc.constant.MessageTypeEnum;
import com.mszlu.rpc.constant.SerializationTypeEnum;
import com.mszlu.rpc.exception.MsRpcException;
import com.mszlu.rpc.factory.SingletonFactory;
import com.mszlu.rpc.message.MsMessage;
import com.mszlu.rpc.message.MsRequest;
import com.mszlu.rpc.message.MsResponse;
import com.mszlu.rpc.netty.client.cache.ChannelCache;
import com.mszlu.rpc.netty.client.handler.MsNettyClientHandler;
import com.mszlu.rpc.netty.client.handler.UnprocessedRequests;
import com.mszlu.rpc.netty.client.idle.ConnectionWatchdog;
import com.mszlu.rpc.netty.codec.MsRpcDecoder;
import com.mszlu.rpc.netty.codec.MsRpcEncoder;
import com.mszlu.rpc.register.nacos.NacosTemplate;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyClient implements MsClient {

    private MsRpcConfig msRpcConfig;

    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;

    private final UnprocessedRequests unprocessedRequests;
    private final NacosTemplate nacosTemplate;
    //ip,port
    private final static Set<String> SERVICES = new CopyOnWriteArraySet<>();

    protected final HashedWheelTimer timer = new HashedWheelTimer();

    private final ChannelCache channelCache;

    public NettyClient(){
        this.channelCache = SingletonFactory.getInstance(ChannelCache.class);
        this.unprocessedRequests = SingletonFactory.getInstance(UnprocessedRequests.class);
        this.nacosTemplate = SingletonFactory.getInstance(NacosTemplate.class);
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                //超时时间设置
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,5000);
//                .handler(new ChannelInitializer<SocketChannel>() {
//                    @Override
//                    protected void initChannel(SocketChannel ch) throws Exception {
//                        //3s 没收到写请求，进行心跳检测
//                        ch.pipeline().addLast(new IdleStateHandler(0, 3, 0, TimeUnit.SECONDS));
//                        ch.pipeline ().addLast ( "decoder",new MsRpcDecoder() );
//                        ch.pipeline ().addLast ( "encoder",new MsRpcEncoder());
//                        ch.pipeline ().addLast ( "handler",new MsNettyClientHandler() );
//
//                    }
//                });
    }

    @Override
    public Object sendRequest(MsRequest msRequest) {
        if (msRpcConfig == null){
            throw new MsRpcException("必须开启EnableRpc");
        }
        CompletableFuture<MsResponse<Object>> resultCompletableFuture = new CompletableFuture<>();
        //1. 先连接 netty服务 拿到channel
        InetSocketAddress inetSocketAddress = null;
        String ipPort = null;
        if (!SERVICES.isEmpty()){
            int size = SERVICES.size();
            //随机的负载均衡算法
            int nextInt = RandomUtils.nextInt(0, size - 1);
            Optional<String> optional = SERVICES.stream().skip(nextInt).findFirst();
            if (optional.isPresent()){
                ipPort = optional.get();
                new InetSocketAddress(ipPort.split(",")[0], Integer.parseInt(ipPort.split(",")[1]));
                log.info("走了缓存，省去了连接nacos的开销...");
            }
        }
        //dubbo rpc ： 注册中心挂掉之后，服务调用还能否正常？ 回答 正常，第一次调用之后，缓存服务提供方的地址，直接发起调用
        //需要从nacos中 获取 服务提供方的ip和端口
        Instance oneHealthyInstance = null;
        try {
            oneHealthyInstance = nacosTemplate.getOneHealthyInstance(msRpcConfig.getNacosGroup(),msRequest.getInterfaceName() + msRequest.getVersion());
            inetSocketAddress = new InetSocketAddress(oneHealthyInstance.getIp(),oneHealthyInstance.getPort());
            ipPort = oneHealthyInstance.getIp() + "," + oneHealthyInstance.getPort();
            SERVICES.add(ipPort);
        } catch (Exception e) {
            log.error("获取nacos实例 出错:",e);
            resultCompletableFuture.completeExceptionally(e);
            return resultCompletableFuture;
        }
        CompletableFuture<Channel> channelCompletableFuture = new CompletableFuture<>();
        ConnectionWatchdog watchdog = new ConnectionWatchdog(bootstrap, timer,inetSocketAddress, channelCompletableFuture, true,channelCache) {
            @Override
            public void clear(InetSocketAddress inetSocketAddress) {
                SERVICES.remove(inetSocketAddress.getHostName()+","+inetSocketAddress.getPort());
                log.info("重连12次未重连上，进行缓存清楚...");
            }
            ////3s 没收到写请求，进行心跳检测
            //                        ch.pipeline().addLast(new IdleStateHandler(0, 3, 0, TimeUnit.SECONDS));
            //                        ch.pipeline ().addLast ( "decoder",new MsRpcDecoder() );
            //                        ch.pipeline ().addLast ( "encoder",new MsRpcEncoder());
            //                        ch.pipeline ().addLast ( "handler",new MsNettyClientHandler() );
            @Override
            public ChannelHandler[] handlers() {
                return new ChannelHandler[]{
                        this,
                        new IdleStateHandler(0, 3, 0, TimeUnit.SECONDS),
                        new MsRpcDecoder(),
                        new MsRpcEncoder(),
                        new MsNettyClientHandler()

                };
            }
        };
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(watchdog.handlers());
            }
        });

//        String finalIpPort = ipPort;
//        bootstrap.connect(inetSocketAddress).addListener(new ChannelFutureListener() {
//            @Override
//            public void operationComplete(ChannelFuture future) throws Exception {
//                //连接是否完成
//                if (future.isSuccess()){
//                    channelCompletableFuture.complete(future.channel());
//                }else{
//                    //从缓存当中进行剔除
//                    SERVICES.remove(finalIpPort);
//                    channelCompletableFuture.completeExceptionally(future.cause());
//                    log.info("连接netty服务失败");
//                }
//            }
//        });


        unprocessedRequests.put(msRequest.getRequestId(),resultCompletableFuture);

        Channel channel = getChannel(inetSocketAddress,channelCompletableFuture);
        if (!channel.isActive()){
            throw new MsRpcException("连接异常");
        }
        MsMessage msMessage = MsMessage.builder()
                .codec(SerializationTypeEnum.PROTO_STUFF.getCode())
                .compress(CompressTypeEnum.GZIP.getCode())
                .messageType(MessageTypeEnum.REQUEST.getCode())
                .data(msRequest)
                .build();
        channel.writeAndFlush(msMessage).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()){
                    log.info("请求完成");
                }else {
                    log.error("发送请求数据失败");
                    future.channel().close();
                    resultCompletableFuture.completeExceptionally(future.cause());
                }
            }
        });

        return resultCompletableFuture;
    }

    @SneakyThrows
    private Channel getChannel(InetSocketAddress inetSocketAddress, CompletableFuture<Channel> channelCompletableFuture) {
        Channel channel = channelCache.get(inetSocketAddress);
        if (channel == null){
            //进行连接...
            doConnect(inetSocketAddress,channelCompletableFuture);
            channel = channelCompletableFuture.get();
            channelCache.set(inetSocketAddress,channel);
            return channel;
        }else{
            //
            log.info("channel是从缓存中获取的，性能进步一步做了提升...");
            return channel;
        }
    }

    public void doConnect(InetSocketAddress inetSocketAddress, CompletableFuture<Channel> channelCompletableFuture){
        bootstrap.connect(inetSocketAddress).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                //连接是否完成
                if (future.isSuccess()){
                    channelCompletableFuture.complete(future.channel());
                }else{
                    //从缓存当中进行剔除
                    SERVICES.remove(inetSocketAddress.getHostName()+","+inetSocketAddress.getPort());
                    channelCompletableFuture.completeExceptionally(future.cause());
                    log.info("连接netty服务失败");
                }
            }
        });
    }

    public MsRpcConfig getMsRpcConfig() {
        return msRpcConfig;
    }

    public void setMsRpcConfig(MsRpcConfig msRpcConfig) {
        this.msRpcConfig = msRpcConfig;
    }
}
