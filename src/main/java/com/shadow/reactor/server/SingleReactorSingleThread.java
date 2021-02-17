package com.shadow.reactor.server;

import com.shadow.reactor.Message;
import com.shadow.utils.ConsolePrinter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author shadow
 * @create 2021-02-13
 * @description 单 Reactor + 单线程
 * 1、reactor 线程处理全部任务
 * <p>
 * read -> decode -> compute -> encode -> send
 */
public class SingleReactorSingleThread {

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
     * Reactor 线程
     * 连接请求事件
     * SelectionKey.OP_ACCEPT
     * 以及请求分发任务
     */
    static class Reactor {
        Selector selector;
        ServerSocketChannel server;
        Thread thread;

        Reactor(int port) {
            try {
                selector = Selector.open();
                server = ServerSocketChannel.open();
                server.configureBlocking(false); // 非阻塞模式
                server.socket().bind(new InetSocketAddress(port), 1024);
                server.register(selector, SelectionKey.OP_ACCEPT); // 1、感兴趣的连接事件
                ConsolePrinter.printlnRed(Thread.currentThread().getName() + " => server started at " + port);
                this.thread = new Thread(this::run, "reactor-thread");
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
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        iterator.remove(); // 必须移除
                        try {
                            ///2、请求分发
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

        private void dispatch(SelectionKey selectionKey) {
            // 事件有效
            if (selectionKey.isValid()) {
                // 处理连接请求
                if (selectionKey.isAcceptable()) {
                    new Acceptor(selectionKey).run();
                }
                // 处理读请求
                if (selectionKey.isReadable()) {
                    new Reader(selectionKey).run();
                }
                // 处理些请求
                if (selectionKey.isWritable()) {
                    new Writer(selectionKey).run();
                }
            }
        }
    }

    /**
     * 处理连接请求
     * 主要处理客户端
     * 及设置 SelectionKey.OP_READ
     */
    static class Acceptor {
        SelectionKey selectionKey;

        Acceptor(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }


        public SocketChannel run() {
            try {
                ServerSocketChannel server = (ServerSocketChannel) selectionKey.channel();
                SocketChannel client = server.accept();
                client.configureBlocking(false); // 非阻塞模式
                client.register(selectionKey.selector(), SelectionKey.OP_READ); // 设置感兴趣的事件
                ConsolePrinter.printlnYellow(Thread.currentThread().getName() + " => client " + client.getRemoteAddress() + " connected... client size = " + clients.size());
                // 通知其他客户端有新客户端上线
                notifyClients(client);
                clients.add(client);
                return client;
            } catch (Exception e) {
                if (selectionKey != null) selectionKey.channel();
            }
            return null;
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
     */
    static class Reader {
        SelectionKey selectionKey;

        public Reader(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        public void run() {
            try {
                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                doRead(socketChannel);
            } catch (Exception e) {
                if (selectionKey != null) selectionKey.cancel();
            }
        }

        private void doRead(SocketChannel socketChannel) {
            try {
                ConsolePrinter.printlnPurple(Thread.currentThread().getName() + " => read....");
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                socketChannel.read(byteBuffer);
                byteBuffer.flip();
                // 处理业务逻辑 - 当前线程直接处理
                doCompute(socketChannel, byteBuffer);
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

        private void doCompute(SocketChannel socketChannel, ByteBuffer byteBuffer) throws IOException {
            String message = new String(byteBuffer.array());
            ConsolePrinter.printlnCyan(Thread.currentThread().getName() + " => " + socketChannel.getRemoteAddress() + " compute: " + message);
            messages.add(new Message().setMessage(message).setSocketChannel(socketChannel));
            selectionKey.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * 处理些请求
     */
    static class Writer {
        SelectionKey selectionKey;

        Writer(SelectionKey selectionKey) {
            this.selectionKey = selectionKey;
        }

        private void run() {
            try {
                SocketChannel socketChannel = (SocketChannel) selectionKey.channel();
                doWrite(socketChannel);
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
