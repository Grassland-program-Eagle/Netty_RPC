package com.mszlu.rpc.netty.client.handler;

import com.mszlu.rpc.exception.MsRpcException;
import com.mszlu.rpc.message.MsResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UnprocessedRequests {

    private static final Map<String, CompletableFuture<MsResponse<Object>>> UP = new ConcurrentHashMap<>();

    public void put(String requestId,CompletableFuture<MsResponse<Object>> resultFuture){
        UP.put(requestId,resultFuture);
    }

    public CompletableFuture<MsResponse<Object>> complete(MsResponse<Object> msResponse){
        CompletableFuture<MsResponse<Object>> completableFuture = UP.remove(msResponse.getRequestId());
        if (completableFuture != null){
            completableFuture.complete(msResponse);
            return completableFuture;
        }
        throw new MsRpcException("获取结果数据出现问题");
    }
}
