package com.shadow.reactor;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.nio.channels.SocketChannel;

/**
 * 消息体
 */
@Data
@Accessors(chain = true)
public class Message implements Serializable {
    SocketChannel socketChannel;
    String message;
}