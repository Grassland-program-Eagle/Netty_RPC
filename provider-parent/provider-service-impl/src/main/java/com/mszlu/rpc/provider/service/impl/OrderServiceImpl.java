package com.mszlu.rpc.provider.service.impl;

import com.mszlu.rpc.annontation.MsService;
import com.mszlu.rpc.provider.service.GoodsService;
import com.mszlu.rpc.provider.service.OrderService;
import com.mszlu.rpc.provider.service.model.Goods;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
//把GoodsService这个服务 发布，消费方 就可以进行调用了
@MsService(version="1.0")
public class OrderServiceImpl implements OrderService {

    public Goods findGoods(Long id) {
        return new Goods(id,"服务提供方订单商品", BigDecimal.valueOf(99));
    }
}
