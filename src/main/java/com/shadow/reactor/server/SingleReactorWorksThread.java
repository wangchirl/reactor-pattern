package com.shadow.reactor.server;

import com.shadow.reactor.Message;
import com.shadow.utils.ConsolePrinter;
import com.shadow.utils.ThreadPoolTools;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author shadow
 * @create 2021-02-15
 * @description 单 Reactor 线程 + 任务线程池
 * 2、reactor 线程处理连接请求、任务线程池处理读写请求
 */
public class SingleReactorWorksThread {

    /**
     * 存储客户端
     */
    private static List<SocketChannel> clients = new CopyOnWriteArrayList<>();

    /**
     * 存储客户端发送的消息
     */
    private static Queue<Message> messages = new LinkedList<>();


    public static void main(String[] args) {
        new Reactor(8088);
    }

    /**
     * reactor 线程
     */
    static class Reactor {
        Selector selector;
        ServerSocketChannel server;
        Thread thread;

        public Reactor(int port) {
            try {
                selector = Selector.open();
                server = ServerSocketChannel.open();
                server.configureBlocking(false); // 非阻塞模式
                server.socket().bind(new InetSocketAddress(port), 1024);
                server.register(selector, SelectionKey.OP_ACCEPT);
                ConsolePrinter.printlnRed(Thread.currentThread().getName() + " => server started at " + port);
                thread = new Thread(this::run,"reactor-thread");
                start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void start() {
            this.thread.start();
        }

        private void run() {
            for (; ; ) {
                try {
                    selector.select(1000);
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
        }

        // 分发请求
        private void dispatch(SelectionKey selectionKey) {
            if (selectionKey.isValid()) {
                // 连接请求
                if (selectionKey.isAcceptable()) {
                    new Acceptor(selectionKey).run();
                }
                // 读请求
                if (selectionKey.isReadable()) {
                    new Reader(selectionKey).run();
                }
                // 写请求
                if (selectionKey.isWritable()) {
                    new Writer(selectionKey).run();
                }
            }
        }
    }

    /**
     * 处理连接请求逻辑
     * 主要处理客户端的连接
     */
    static class Acceptor {
        SelectionKey selectionKey;

        Acceptor(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        private void run() {
            try {
                ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
                SocketChannel client = server.accept();
                client.configureBlocking(false); // 非阻塞模式
                client.register(selectionKey.selector(), SelectionKey.OP_READ);
                ConsolePrinter.printlnYellow(Thread.currentThread().getName() + " => client " + client.getRemoteAddress() + " connected... client size = " + clients.size());
                // 通知其他客户端有新客户端上线
                notifyClients(client);
                clients.add(client);
            } catch (Exception e) {
                if (selectionKey != null) {
                    clients.remove(selectionKey.channel());
                    selectionKey.cancel();
                }
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

    /**
     * 处理读请求
     * 主要处理客户端发送消息
     */
    static class Reader {
        SelectionKey selectionKey;

        Reader(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        private void run() {
            try {
                SocketChannel client = (SocketChannel) selectionKey.channel();
                doRead(client);
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
                ThreadPoolTools.threadPool.submit(() -> doCompute(client, byteBuffer));
            } catch (Exception e) {
                if (client != null) {
                    try {
                        client.close();
                        clients.remove(client);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        private void doCompute(SocketChannel client, ByteBuffer byteBuffer) {
            try {
                String message = new String(byteBuffer.array());
                ConsolePrinter.printlnCyan(Thread.currentThread().getName() + " => " + client.getRemoteAddress() + " compute: " + message);
                messages.add(new Message().setMessage(message).setSocketChannel(client));
                selectionKey.interestOps(SelectionKey.OP_WRITE);
            } catch (Exception e) {
                e.printStackTrace();
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
}
