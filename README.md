### 理解 reactor 线程模型
> 参考 并发编程大佬的 nio.pdf

1、SingleReactorSingleThread
```java
/**
 *  单 reactor线程
 *  
 *  reactor 线程处理连接请求 + 读请求 + 写请求 + 业务逻辑
 *
 *  一个线程左所有的事情
 *
/*
```

2、SingleReactorWorksThread
```java
/**
 *  单 reactor线程 + 业务线程池
 *  
 *  reactor 线程处理连接请求 + 读请求 + 写请求 
 *  Work Thread 业务线程池处理业务逻辑
 *
/*
```

3、MasterSlaveReactorWorksThread
```java
/**
 *  主从 reactor线程 + 业务线程池 
 *  
 *  主 reactor 线程处理连接请求 + 分派请求
 *  从 reactor 线程处理读写请求
 *  Work Thread 业务线程池处理业务逻辑
 *
/*
```

4、MasterSlavesReactorWorksThread
```java
/**
 *  主从 reactor线程（一主多从） + 业务线程池 
 *  
 *  主 reactor 线程处理连接请求 + 分派请求
 *  从 reactor 线程处理读写请求，多个从 reactor 并行处理
 *  Work Thread 业务线程池处理业务逻辑
 *
/*
```

5、MastersSlavesReactorWorksThread
```java
/**
 *  主从 reactor线程（多主多从） + 业务线程池 
 *  
 *  主 reactor 线程处理连接请求 + 分派请求（绑定多个端口，客户端可以连接到不同端口）
 *  从 reactor 线程处理读写请求，多个从 reactor 并行处理
 *  Work Thread 业务线程池处理业务逻辑
 *
 */
```

### Netty 主从 Reactor 线程模型
```java
/**
 *
 *  bossGroup（主线程组） + workerGroup（从线程组）
 * 参考 https://netty.io/wiki/related-articles.html
 */
```