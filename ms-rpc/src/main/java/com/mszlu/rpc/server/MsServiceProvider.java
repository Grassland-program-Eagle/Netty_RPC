package com.mszlu.rpc.server;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.mszlu.rpc.annontation.MsService;
import com.mszlu.rpc.config.MsRpcConfig;
import com.mszlu.rpc.exception.MsRpcException;
import com.mszlu.rpc.factory.SingletonFactory;
import com.mszlu.rpc.netty.NettyServer;
import com.mszlu.rpc.register.nacos.NacosTemplate;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
public class MsServiceProvider {

    private MsRpcConfig msRpcConfig;

    private final Map<String, Object> serviceMap;
    private NacosTemplate nacosTemplate;
    public MsServiceProvider(){
        serviceMap = new ConcurrentHashMap<>();
        nacosTemplate = SingletonFactory.getInstance(NacosTemplate.class);
    }

    public void publishService(MsService msService, Object service) {
        registerService(msService,service);
        //启动nettyServer 这个方法会调用多次，服务只能启动一次
        NettyServer nettyServer = SingletonFactory.getInstance(NettyServer.class);
        nettyServer.setMsServiceProvider(this);
        if (!nettyServer.isRunning()){
            nettyServer.run();
        }
    }

    private void registerService(MsService msService, Object service) {
        String version = msService.version();
        String interfaceName = service.getClass().getInterfaces()[0].getCanonicalName();
        log.info("发布了服务:{}",interfaceName);
        serviceMap.put(interfaceName+version,service);
        //同步注册到Nacos中
        //group 只有在同一个组内 调用关系才能成立，不同的组之间是隔离的
        if (msRpcConfig == null){
            throw new MsRpcException("必须开启EnableRPC");
        }
        try {
            Instance instance = new Instance();
            instance.setIp(InetAddress.getLocalHost().getHostAddress());
            instance.setPort(msRpcConfig.getProviderPort());
            instance.setClusterName("ms-rpc");
            instance.setServiceName(interfaceName+version);
            nacosTemplate.registerServer(msRpcConfig.getNacosGroup(),instance);
        }catch (Exception e){
            log.error("nacos注册失败:",e);
        }

    }

    public Object getService(String serviceName){
        return serviceMap.get(serviceName);
    }

    public MsRpcConfig getMsRpcConfig() {
        return msRpcConfig;
    }

    public void setMsRpcConfig(MsRpcConfig msRpcConfig) {
        this.msRpcConfig = msRpcConfig;
    }
}
