/**
 * Copyright 2013 Julien Silland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.soliton.protobuf.json;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.soliton.protobuf.*;

import java.util.logging.Logger;

/**
 * Concrete {@link io.soliton.protobuf.Server} implementation surfacing the
 * registered services over an HTTP transport using the JSON-RPC protocol.
 *
 * @see <a href="http://json-rpc.org/">JSON-RPC</a>
 */
public class HttpJsonRpcServer implements Server {

  private static final Logger logger = Logger.getLogger(
      HttpJsonRpcServer.class.getCanonicalName());

  private final ServiceGroup services = new DefaultServiceGroup();
  private final int port;
  private final String rpcPath;

  private Channel channel;
  private EventLoopGroup parentGroup;
  private EventLoopGroup childGroup;

  /**
   * Exhaustive constructor.
   *
   * @param port the TCP port this server should bind to
   * @param rpcPath the URL path on which the handler should be bound.
   */
  public HttpJsonRpcServer(int port, String rpcPath) {
    this.port = port;
    this.rpcPath = rpcPath;
  }

  @Override
  public ServiceGroup serviceGroup() {
    return services;
  }

  /**
   * Starts this server.
   *
   * <p>This is a synchronous operation.</p>
   */
  public void start() {
    ServerBootstrap bootstrap = new ServerBootstrap();
    parentGroup = new NioEventLoopGroup();
    childGroup = new NioEventLoopGroup();

    bootstrap.group(parentGroup, childGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<Channel>() {

          @Override
          protected void initChannel(Channel channel) throws Exception {
            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addLast("http-codec", new HttpServerCodec());
            pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
            pipeline.addLast("json-rpc", new JsonRpcHandler(HttpJsonRpcServer.this, rpcPath));
          }
        });

    ChannelFuture futureChannel = bootstrap.bind(port).awaitUninterruptibly();
    if (futureChannel.isSuccess()) {
      this.channel = futureChannel.channel();
      logger.info("Piezo JSON-RPC server started successfully.");
    } else {
      logger.info("Failed to start Piezo JSON-RPC server.");
      throw new RuntimeException(futureChannel.cause());
    }
  }

  /**
   * Stops this server.
   *
   * <p>This is a synchronous operation.</p>
   */
  public void stop() {
    logger.info("Shutting down Piezo server.");
    channel.close().addListener(new GenericFutureListener<Future<Void>>() {

      @Override
      public void operationComplete(Future<Void> future) throws Exception {
        parentGroup.shutdownGracefully();
        childGroup.shutdownGracefully();
      }
    }).awaitUninterruptibly();
  }

  public static void main(String... args) {
    HttpJsonRpcServer server = new HttpJsonRpcServer(3000, "rpc");
    server.start();
  }
}
