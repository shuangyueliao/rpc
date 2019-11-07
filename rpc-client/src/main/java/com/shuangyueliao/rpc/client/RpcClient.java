package com.shuangyueliao.rpc.client;

import com.shuangyueliao.rpc.common.RpcDecoder;
import com.shuangyueliao.rpc.common.RpcEncoder;
import com.shuangyueliao.rpc.common.RpcRequest;
import com.shuangyueliao.rpc.common.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 框架的RPC 客户端（用于发送 RPC 请求）
 */
@Slf4j
public class RpcClient extends SimpleChannelInboundHandler<RpcResponse> {

    private String host;
    private int port;

    private volatile RpcResponse response;

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    private ChannelFuture future = null;

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) {
                            // 向pipeline中添加编码、解码、业务处理的handler
                            channel.pipeline()
                                    .addLast(new RpcEncoder(RpcRequest.class))  //OUT - 1
                                    .addLast(new RpcDecoder(RpcResponse.class)) //IN - 1
                                    .addLast(RpcClient.this);                   //IN - 2
                        }
                    }).option(ChannelOption.SO_KEEPALIVE, true);
            // 链接服务器
            future = bootstrap.connect(host, port).sync();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                future.channel().closeFuture().sync();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            group.shutdownGracefully();
        }
    }

    /**
     * 链接服务端，发送消息
     *
     * @param request
     * @return
     * @throws Exception
     */
    public RpcResponse send(RpcRequest request) throws Exception {
        //将request对象写入outbundle处理后发出（即RpcEncoder编码器）
        // 用线程等待的方式决定是否关闭连接
        // 其意义是：先在此阻塞，等待获取到服务端的返回后，被唤醒，从而关闭网络连接
        Object o = new Object();
        locks.put(request.getRequestId(), o);
        synchronized (o) {
            future.channel().writeAndFlush(request);
            o.wait(10000);
        }
        return response;
    }

    /**
     * 读取服务端的返回结果
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcResponse response)
            throws Exception {
        this.response = response;
        Object o = locks.remove(response.getRequestId());
        synchronized (o) {
            o.notify();
        }
    }

    /**
     * 异常处理
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        log.error("client caught exception", cause);
        ctx.close();
    }
}
