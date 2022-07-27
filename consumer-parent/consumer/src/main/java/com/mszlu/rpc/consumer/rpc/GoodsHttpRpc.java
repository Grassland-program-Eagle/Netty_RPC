package com.mszlu.rpc.consumer.rpc;

import com.mszlu.rpc.annontation.MsHttpClient;
import com.mszlu.rpc.annontation.MsMapping;
import com.mszlu.rpc.provider.service.model.Goods;

@MsHttpClient(value = "goodsHttpRpc")
public interface GoodsHttpRpc {
    //发起网络调用 调用provider 商品查询服务
    @MsMapping(url = "http://localhost:7777",api = "/provider/goods/{id}")
    Goods findGoods(Long id);
}
