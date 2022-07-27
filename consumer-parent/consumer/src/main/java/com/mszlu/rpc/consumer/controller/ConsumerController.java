package com.mszlu.rpc.consumer.controller;

import com.mszlu.rpc.annontation.MsReference;
import com.mszlu.rpc.consumer.rpc.GoodsHttpRpc;
import com.mszlu.rpc.provider.service.GoodsService;
import com.mszlu.rpc.provider.service.model.Goods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("consumer")
public class

ConsumerController {

    @Autowired
    private RestTemplate restTemplate;

//    @GetMapping("/find/{id}")
//    public Goods find(@PathVariable Long id){
//        //通过http调用 去访问provider提供的商品查询服务
//        //http://localhost:7777/provider/goods/1
//        Goods goods = restTemplate.getForObject("http://localhost:7777/provider/goods/" + id, Goods.class);
//        return goods;
//    }

//    @Autowired
//    private GoodsHttpRpc goodsHttpRpc;
//
//    @GetMapping("/find/{id}")
//    public Goods find(@PathVariable Long id){
//        //通过http调用 去访问provider提供的商品查询服务
//        return goodsHttpRpc.findGoods(id);
//    }

    @MsReference(version = "1.0")
    private GoodsService goodsService;

    @GetMapping("/find/{id}")
    public Goods find(@PathVariable Long id){
        //通过http调用 去访问provider提供的商品查询服务
        return goodsService.findGoods(id);
    }

}
