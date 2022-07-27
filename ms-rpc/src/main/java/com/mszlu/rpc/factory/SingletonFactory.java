package com.mszlu.rpc.factory;

import com.mszlu.rpc.server.MsServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 获取单例对象的工厂类
 *
 */
public final class SingletonFactory {
    private static final Map<String, Object> OBJECT_MAP = new ConcurrentHashMap<>();

    private SingletonFactory() {
    }

    public static <T> T getInstance(Class<T> c) {
        if (c == null) {
            throw new IllegalArgumentException();
        }
        String key = c.toString();
        if (OBJECT_MAP.containsKey(key)) {
            return c.cast(OBJECT_MAP.get(key));
        } else {
            return c.cast(OBJECT_MAP.computeIfAbsent(key, k -> {
                try {
                    return c.getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }));
        }
    }

    public static void main(String[] args) {
        //测试并发下 生成的单例是否唯一
        ExecutorService executorService = Executors.newFixedThreadPool(100);

        for (int i = 0 ; i< 100; i++) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    MsServiceProvider instance = SingletonFactory.getInstance(MsServiceProvider.class);
                    System.out.println(instance);
                }
            });
        }
        while (true){}
    }
}
