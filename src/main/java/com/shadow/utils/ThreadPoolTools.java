package com.shadow.utils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author shadow
 * @create 2021-02-17
 * @description
 */
public final class ThreadPoolTools {

    public static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(2,
            Runtime.getRuntime().availableProcessors() * 2,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1024),
            new SimpleThreadFactory("worker-thread-"),
            (r, e) -> r.run());


    static class SimpleThreadFactory implements ThreadFactory {
        // 线程池序列
        private static final AtomicInteger POOL_SEQUENCE = new AtomicInteger(1);
        // 线程序列
        private final AtomicInteger threadSequence = new AtomicInteger(1);
        // 线程前缀
        private final String prefix;
        // 是否后台线程
        private final boolean daemon;
        // 线程组
        private final ThreadGroup threadGroup;

        public SimpleThreadFactory() {
            this("pool-" + POOL_SEQUENCE.getAndIncrement());
        }
        public SimpleThreadFactory(String prefix) {
            this(prefix, false);
        }
        public SimpleThreadFactory(String prefix, boolean daemon) {
            this.prefix = prefix;
            this.daemon = daemon;
            this.threadGroup = System.getSecurityManager() == null ? Thread.currentThread().getThreadGroup() : System.getSecurityManager().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            String name = prefix + threadSequence.getAndIncrement();
            Thread ret = new Thread(threadGroup, r, name, 0);
            ret.setDaemon(daemon);
            return ret;
        }

        public ThreadGroup getThreadGroup() {
            return this.threadGroup;
        }
    }

}
