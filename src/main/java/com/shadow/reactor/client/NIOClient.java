package com.shadow.reactor.client;

import com.shadow.utils.ConsolePrinter;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author shadow
 * @create 2021-02-15
 * @description
 */
public class NIOClient {

    public static void main(String[] args) throws Exception {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        Selector selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);

        socketChannel.connect(new InetSocketAddress("localhost", 8088));

        while (true) {
            selector.select(1000);
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey selectionKey = iterator.next();
                iterator.remove();
                SocketChannel client = (SocketChannel) selectionKey.channel();
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                if (selectionKey.isConnectable()) {
                    if (client.isConnectionPending()) {
                        client.finishConnect();
                        ConsolePrinter.printlnYellow("连接服务器成功！");
                        ExecutorService executorService = Executors.newSingleThreadExecutor();
                        executorService.submit(() -> {
                            while (true) {
                                try {
                                    byteBuffer.clear();

                                    InputStreamReader streamReader = new InputStreamReader(System.in);
                                    BufferedReader reader = new BufferedReader(streamReader);

                                    String message = reader.readLine();

                                    byteBuffer.put(message.getBytes());
                                    byteBuffer.flip();

                                    client.write(byteBuffer);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }

                    client.register(selector, SelectionKey.OP_READ);
                } else if (selectionKey.isReadable()) {
                    byteBuffer.clear();
                    int read = client.read(byteBuffer);
                    if (read > 0) {
                        byteBuffer.flip();
                        String receiveMsg = new String(byteBuffer.array(), 0, read);
                        if (StringUtils.isNotEmpty(receiveMsg.trim()))
                            ConsolePrinter.printlnYellow("收到消息：" + receiveMsg);
                    }
                }
            }
        }
    }

}
