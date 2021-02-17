package com.shadow.reactor.server;

import com.shadow.reactor.Message;
import com.shadow.utils.ConsolePrinter;
import com.shadow.utils.ThreadPoolTools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author shadow
 * @create 2021-02-17
 * @description 主从 reactor 线程 + work 线程（一主一从 + work 线程）
 * 主 reactor 线程负责连接请求
 * 从 reactor 线程负责读写请求
 * 多个 从 reactor 线程
 * work 线程负责处理业务逻辑
 */
public class MasterSlavesReactorWorksThread {

    /**
     * 客户端
     */
    private static List<SocketChannel> clients = new CopyOnWriteArrayList<>();

    /**
     * 消息队列
     */
    private static Queue<Message> messages = new LinkedList<>();

    /**
     * selector.select() loop mod
     * rebuild selector
     */
    private static AtomicInteger mod = new AtomicInteger(1);

    public static void main(String[] args) {
        AbstractReactor[] subReactors = new AbstractReactor[4];
        for (int i = 0; i < 4; i++) {
            subReactors[i] = new SubReactor(i);
        }
        new MainReactor(8088).setSubReactor(subReactors);
    }


    static class MainReactor extends AbstractReactor {
        // 从 reactor 选择指标
        AtomicInteger index = new AtomicInteger(0);

        ServerSocketChannel server;

        public void setSubReactor(AbstractReactor[] reactors) {
            super.subReactors = reactors;
        }

        public MainReactor(int port) {
            try {
                selector = Selector.open();
                server = ServerSocketChannel.open();
                server.configureBlocking(false);
                server.socket().bind(new InetSocketAddress(port), 1024);
                server.register(selector, SelectionKey.OP_ACCEPT);
                ConsolePrinter.printlnRed(Thread.currentThread().getName() + " => server started at " + port);
                super.thread = new Thread(this::run, "main-reactor-thread");
                super.start();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        @Override
        protected void run() {
            try {
                for (; ; ) {
                    //rebuildSelector();
                    selector.select(1000);
                    mod.incrementAndGet();
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove();
                        try {
                            dispatch(selectionKey);
                        } catch (Exception e) {
                            if (selectionKey != null) {
                                selectionKey.cancel();
                                if (selectionKey.channel() != null) {
                                    clients.remove(selectionKey.channel());
                                    ConsolePrinter.printlnRed("客户端断开连接 ==> " + selectionKey.channel() + " client size = " + clients.size());
                                    selectionKey.channel().close();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (selector != null) {
                    try {
                        selector.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        private synchronized AbstractReactor getSubReactor() {
            return subReactors[(index.getAndIncrement() % subReactors.length)];
        }

        @Override
        protected void dispatch(SelectionKey selectionKey) {
            if (selectionKey.isValid()) {
                // 获取下一个从 reactor
                AbstractReactor subReactor = getSubReactor();
                if (selectionKey.isAcceptable()) {
                    new Acceptor(selectionKey, subReactor).run();
                }
                if (subReactors == null) {
                    if (selectionKey.isReadable()) {
                        new Reader(selectionKey).run();
                    }
                    if (selectionKey.isWritable()) {
                        new Writer(selectionKey).run();
                    }
                }
            }
        }

        @Override
        public void registry(SocketChannel client) {
            // do nothing
        }
    }

    static class SubReactor extends AbstractReactor {
        // 从 reactor id
        private int id;

        SubReactor(int id) {
            try {
                this.id = id;
                super.selector = SelectorProvider.provider().openSelector();
                super.thread = new Thread(this::run, "sub-reactor-thread-" + this.id);
                super.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void run() {
            try {
                for (; ; ) {
                    //rebuildSelector();
                    selector.select(1000);
                    mod.incrementAndGet();
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove();
                        try {
                            dispatch(selectionKey);
                        } catch (Exception e) {
                            if (selectionKey != null) {
                                selectionKey.cancel();
                                if (selectionKey.channel() != null) {
                                    clients.remove(selectionKey.channel());
                                    ConsolePrinter.printlnRed("客户端断开连接 ==> " + selectionKey.channel() + " client size = " + clients.size());
                                    selectionKey.channel().close();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (selector != null) {
                    try {
                        selector.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        @Override
        protected void dispatch(SelectionKey selectionKey) {
            if (selectionKey.isValid()) {
                if (selectionKey.isReadable()) {
                    new Reader(selectionKey).run();
                }
                if (selectionKey.isWritable()) {
                    new Writer(selectionKey).run();
                }
            }
        }

        @Override
        public void registry(SocketChannel client) {
            {
                try {
                    client.register(selector, SelectionKey.OP_READ);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class Acceptor {

        SelectionKey selectionKey;

        AbstractReactor reactor;

        public Acceptor(SelectionKey selectionKey, AbstractReactor reactor) {
            this.selectionKey = selectionKey;
            this.reactor = reactor;
        }

        private void run() {
            try {
                ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
                SocketChannel client = server.accept();
                client.configureBlocking(false);
                if (reactor == null) {
                    client.register(selectionKey.selector(), SelectionKey.OP_READ);
                } else {
                    reactor.registry(client);
                }
                ConsolePrinter.printlnYellow(Thread.currentThread().getName() + " => client " + client.getRemoteAddress() + " connected... client size = " + (clients.size() + 1));
                // 通知其他客户端有新客户端上线
                notifyClients(client);
                clients.add(client);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void notifyClients(SocketChannel socketChannel) {
            for (SocketChannel client : clients) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                try {
                    byteBuffer.put(("新客户端上线了！" + socketChannel.getRemoteAddress()).getBytes());
                    byteBuffer.flip();
                    client.write(byteBuffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static class Reader {

        SelectionKey selectionKey;

        public Reader(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        private void run() {
            try {
                SocketChannel client = (SocketChannel) selectionKey.channel();
                doRead(client);
                selectionKey.interestOps(SelectionKey.OP_WRITE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void doRead(SocketChannel client) {
            try {
                ConsolePrinter.printlnPurple(Thread.currentThread().getName() + " => read....");
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                client.read(byteBuffer);
                byteBuffer.flip();
                // 处理业务逻辑 - 交给业务线程
                Future<?> future = ThreadPoolTools.threadPool.submit(() -> doCompute(client, byteBuffer));
                future.get();
            } catch (Exception e) {
                if (client != null) {
                    try {
                        client.close();
                        clients.remove(client);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                e.printStackTrace();
            }
        }

        private void doCompute(SocketChannel client, ByteBuffer byteBuffer) {
            try {
                String message = new String(byteBuffer.array());
                ConsolePrinter.printlnCyan(Thread.currentThread().getName() + " => " + client.getRemoteAddress() + " compute: " + message);
                messages.add(new Message().setMessage(message).setSocketChannel(client));
            } catch (Exception e) {
                ConsolePrinter.printlnRed("rebuild selector curse little time unable used");
            }
        }

    }

    /**
     * 写请求处理逻辑
     */
    static class Writer {
        SelectionKey selectionKey;

        Writer(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        private void run() {
            try {
                SocketChannel client = (SocketChannel) selectionKey.channel();
                doWrite(client);
                selectionKey.interestOps(SelectionKey.OP_READ);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void doWrite(SocketChannel socketChannel) {
            try {
                while (!messages.isEmpty()) {
                    Message message = messages.poll();
                    ConsolePrinter.printlnPurple(Thread.currentThread().getName() + " => " + socketChannel.getRemoteAddress() + " write：" + message.getMessage());
                    for (SocketChannel client : clients) {
                        StringBuilder builder = new StringBuilder(message.getMessage());
                        if (client == socketChannel) {
                            builder.insert(0, "【自己】：");
                        } else {
                            builder.insert(0, "【客户端" + socketChannel + "】：");
                        }
                        ByteBuffer byteBuffer = ByteBuffer.wrap(builder.toString().getBytes());
                        client.write(byteBuffer);
                    }
                }
            } catch (Exception e) {
                if (socketChannel != null) {
                    try {
                        socketChannel.close();
                        clients.remove(socketChannel);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    static abstract class AbstractReactor {

        Selector selector;
        Thread thread;
        AbstractReactor[] subReactors;

        protected abstract void run();

        protected void rebuildSelector() throws Exception {
            if (mod.getAndIncrement() % 20 == 0) {
                ConsolePrinter.printlnRed("rebuild sub reactor selector for forbidden CUP 100% bug");
                for (SelectionKey key : selector.keys()) {
                    Object attachment = key.attachment();
                    int interestOps = key.interestOps();
                    key.cancel();
                    Selector newSelector = Selector.open();
                    key.channel().register(newSelector, interestOps, attachment);
                    key.selector().close();
                    selector = newSelector;
                }
            }
        }

        private void start() {
            this.thread.start();
        }

        protected abstract void dispatch(SelectionKey selectionKey);

        public abstract void registry(SocketChannel client);

    }
}
