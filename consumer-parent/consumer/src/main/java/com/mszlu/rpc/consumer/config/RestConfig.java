package com.mszlu.rpc.consumer.config;

import com.mszlu.rpc.annontation.EnableHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableHttpClient(basePackage = "com.mszlu.rpc.consumer.rpc")
public class RestConfig {

    @Bean
    public RestTemplate restTemplate(){
        return new RestTemplate();
    }
}
