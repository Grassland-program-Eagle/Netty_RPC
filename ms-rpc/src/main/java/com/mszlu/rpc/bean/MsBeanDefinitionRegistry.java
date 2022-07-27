package com.mszlu.rpc.bean;

import com.mszlu.rpc.annontation.EnableHttpClient;
import com.mszlu.rpc.annontation.MsHttpClient;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Set;

/**
 * 1. ImportBeanDefinitionRegistrar类只能通过其他类@Import的方式来加载，通常是启动类或配置类。
 * 2. 使用@Import，如果括号中的类是ImportBeanDefinitionRegistrar的实现类，则会调用接口方法，将其中要注册的类注册成bean
 * 3. 实现该接口的类拥有注册bean的能力
 */
public class MsBeanDefinitionRegistry implements ImportBeanDefinitionRegistrar,
        ResourceLoaderAware, EnvironmentAware {

    private Environment environment;
    private ResourceLoader resourceLoader;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata,
                                        BeanDefinitionRegistry registry) {
        registerMsHttpClient(metadata,registry);
    }

    private void registerMsHttpClient(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        //将MsHttpClient所标识的接口，生成代理类，并且注册到spring容器当中
        Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(EnableHttpClient.class.getCanonicalName());
        Object basePackage = annotationAttributes.get("basePackage");
        if (basePackage != null){
            String base = basePackage.toString();
            //ClassPathScanningCandidateComponentProvider是Spring提供的工具，可以按自定义的类型，查找classpath下符合要求的class文件
            ClassPathScanningCandidateComponentProvider scanner = getScanner();
            scanner.setResourceLoader(resourceLoader);
            //找到MsHttpClient 扫描器 就会进行扫描
            AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(MsHttpClient.class);
            scanner.addIncludeFilter(annotationTypeFilter);
            //包路径 com.mszlu.rpc.consumer.rpc 路径下进行扫描
            Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(base);
            for (BeanDefinition candidateComponent : candidateComponents) {
                //BeanDefinition spring bean定义
                if (candidateComponent instanceof AnnotatedBeanDefinition){
                    AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                    AnnotationMetadata annotationMetadata = annotatedBeanDefinition.getMetadata();
                    Assert.isTrue(annotationMetadata.isInterface(),"MsHttpClient注解必须定义在接口上");
                    Map<String, Object> httpClientAttributes = annotationMetadata.getAnnotationAttributes(MsHttpClient.class.getCanonicalName());
                    //获取注解当中的value值，这个value值 是我们的 bean的名称
                    String beanName = getClientName(httpClientAttributes);

                    //接口无法实例化，所以生成代理实现类
                    BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(MsHttpClientFactoryBean.class);
                    beanDefinitionBuilder.addPropertyValue("interfaceClass",annotationMetadata.getClassName());
                    registry.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
                }
            }


        }
    }

    private String getClientName(Map<String, Object> clientAnnotationAttributes) {
        if (clientAnnotationAttributes == null){
            throw new RuntimeException("value必须有值");
        }
        Object value = clientAnnotationAttributes.get("value");
        if (value != null && !value.toString().equals("")){
            return value.toString();
        }
        return null;
    }

    //这个方法是从 Feign组件中 源码找的
    protected ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                boolean isCandidate = false;
                if (beanDefinition.getMetadata().isIndependent()) {
                    if (!beanDefinition.getMetadata().isAnnotation()) {
                        isCandidate = true;
                    }
                }
                return isCandidate;
            }
        };
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}