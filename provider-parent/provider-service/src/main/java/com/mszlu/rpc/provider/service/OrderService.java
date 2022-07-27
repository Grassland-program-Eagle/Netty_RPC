package com.mszlu.rpc.provider.service;

import com.mszlu.rpc.provider.service.model.Goods;

public interface OrderService {

    /**
     * 根据商品id 查询商品
     * @param id
     * @return
     */
    Goods findGoods(Long id);
}
