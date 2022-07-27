package com.mszlu.rpc.spring;

import com.mszlu.rpc.annontation.EnableRpc;
import com.mszlu.rpc.annontation.MsReference;
import com.mszlu.rpc.annontation.MsService;
import com.mszlu.rpc.config.MsRpcConfig;
import com.mszlu.rpc.factory.SingletonFactory;
import com.mszlu.rpc.netty.client.NettyClient;
import com.mszlu.rpc.proxy.MsRpcClientProxy;
import com.mszlu.rpc.register.nacos.NacosTemplate;
import com.mszlu.rpc.server.MsServiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 在spring 的bean 初始化 前后进行调用,一般代码都写到 初始化之后
 */
@Slf4j
public class MsRpcSpringBeanPostProcessor implements BeanPostProcessor,BeanFactoryPostProcessor {

    private MsServiceProvider msServiceProvider;

    private MsRpcConfig msRpcConfig;
    private NettyClient nettyClient;
    private NacosTemplate nacosTemplate;

    public MsRpcSpringBeanPostProcessor(){
        //1. 防止线程问题 2. 便于其他类使用
        msServiceProvider = SingletonFactory.getInstance(MsServiceProvider.class);
        nettyClient = SingletonFactory.getInstance(NettyClient.class);
        nacosTemplate = SingletonFactory.getInstance(NacosTemplate.class);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        EnableRpc enableRpc = bean.getClass().getAnnotation(EnableRpc.class);
        if (enableRpc != null){
            if (msRpcConfig == null) {
                log.info("EnableRpc 会先于所有的Bean实例化之前 执行");
                msRpcConfig = new MsRpcConfig();
                msRpcConfig.setProviderPort(enableRpc.serverPort());
                msRpcConfig.setNacosPort(enableRpc.nacosPort());
                msRpcConfig.setNacosHost(enableRpc.nacosHost());
                msRpcConfig.setNacosGroup(enableRpc.nacosGroup());
                nettyClient.setMsRpcConfig(msRpcConfig);
                msServiceProvider.setMsRpcConfig(msRpcConfig);
                nacosTemplate.init(msRpcConfig.getNacosHost(),msRpcConfig.getNacosPort());

            }
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        //找到MsService注解，以及 MsReference注解
        //bean代表spring的所有能扫描到的bean
        if (bean.getClass().isAnnotationPresent(MsService.class)){
            MsService msService = bean.getClass().getAnnotation(MsService.class);
            //加了MsService的bean就被找到了，就把其中的方法 都发布为服务
            msServiceProvider.publishService(msService,bean);
        }
        Field[] declaredFields = bean.getClass().getDeclaredFields();
        for (Field declaredField : declaredFields) {
            MsReference msReference = declaredField.getAnnotation(MsReference.class);
            if (msReference != null){
                //找到了加了MsReference的字段，就要生成代理类，当接口方法调用的时候，实际上就是访问的代理类
                //中的invoke方法
                MsRpcClientProxy msRpcClientProxy = new MsRpcClientProxy(msReference,nettyClient);
                Object proxy = msRpcClientProxy.getProxy(declaredField.getType());
                //当isAccessible()的结果是false时不允许通过反射访问该字段
                declaredField.setAccessible(true);
                try {
                    declaredField.set(bean,proxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return bean;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof BeanDefinitionRegistry) {
            try {
                // init scanner
                Class<?> scannerClass = ClassUtils.forName ( "org.springframework.context.annotation.ClassPathBeanDefinitionScanner",
                        MsRpcSpringBeanPostProcessor.class.getClassLoader () );
                Object scanner = scannerClass.getConstructor ( new Class<?>[]{BeanDefinitionRegistry.class, boolean.class} )
                        .newInstance ( new Object[]{(BeanDefinitionRegistry) beanFactory, true} );
                // add filter
                Class<?> filterClass = ClassUtils.forName ( "org.springframework.core.type.filter.AnnotationTypeFilter",
                        MsRpcSpringBeanPostProcessor.class.getClassLoader () );
                Object filter = filterClass.getConstructor ( Class.class ).newInstance ( EnableRpc.class );
                Method addIncludeFilter = scannerClass.getMethod ( "addIncludeFilter",
                        ClassUtils.forName ( "org.springframework.core.type.filter.TypeFilter", MsRpcSpringBeanPostProcessor.class.getClassLoader () ) );
                addIncludeFilter.invoke ( scanner, filter );
                // scan packages
                Method scan = scannerClass.getMethod ( "scan", new Class<?>[]{String[].class} );
                scan.invoke ( scanner, new Object[]{"com.mszlu.rpc.annontation"} );
            } catch (Throwable e) {
                // spring 2.0
            }
        }
    }
}
