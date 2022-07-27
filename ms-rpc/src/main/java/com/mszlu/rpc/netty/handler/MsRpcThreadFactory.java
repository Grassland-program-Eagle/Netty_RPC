package com.mszlu.rpc.netty.handler;

import com.mszlu.rpc.server.MsServiceProvider;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class MsRpcThreadFactory implements ThreadFactory {

    private MsServiceProvider msServiceProvider;

    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    private final String namePrefix;

    private final ThreadGroup threadGroup;

    public MsRpcThreadFactory(MsServiceProvider msServiceProvider) {
        this.msServiceProvider = msServiceProvider;
        SecurityManager securityManager = System.getSecurityManager();
        threadGroup = securityManager != null ? securityManager.getThreadGroup() :Thread.currentThread().getThreadGroup();
        namePrefix = "ms-rpc-" + poolNumber.getAndIncrement()+"-thread-";
    }

    //创建的线程以“N-thread-M”命名，N是该工厂的序号，M是线程号
    public Thread newThread(Runnable runnable) {
        Thread t = new Thread(threadGroup, runnable,
                namePrefix + threadNumber.getAndIncrement(), 0);
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}
