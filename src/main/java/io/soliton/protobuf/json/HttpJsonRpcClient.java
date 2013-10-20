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

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.Message;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.GenericFutureListener;
import io.soliton.protobuf.ChannelInitializers;
import io.soliton.protobuf.Client;
import io.soliton.protobuf.ClientMethod;

import java.util.logging.Logger;

/**
 * Implementation of an RPC client that encodes method calls using the JSON-RPC
 * protocol over am HTTP transport.
 *
 * @see <a href="http://json-rpc.org/">JSON-RPC</a>
 */
public class HttpJsonRpcClient implements Client {

  private static final Logger logger = Logger.getLogger(
      HttpJsonRpcClient.class.getCanonicalName());

  private static final Joiner DOT_JOINER = Joiner.on('.');

  private final HostAndPort remoteAddress;
  private final String rpcPath;
  private final Channel channel;
  private final JsonRpcClientHandler handler = new JsonRpcClientHandler();

  /**
   * Exhaustive constructor.
   *
   * @param remoteAddress the address of the remote server
   * @param rpcPath the path of the RPC endpoint on the remote server
   */
  public HttpJsonRpcClient(HostAndPort remoteAddress, String rpcPath) {
    this.remoteAddress = Preconditions.checkNotNull(remoteAddress);
    this.rpcPath = Preconditions.checkNotNull(rpcPath);
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.handler(ChannelInitializers.httpClient(handler));

    ChannelFuture future = bootstrap.connect(remoteAddress.getHostText(), remoteAddress.getPort());
    future.awaitUninterruptibly();
    if (future.isSuccess()) {
      logger.info("Piezo client successfully connected to " + remoteAddress.toString());
      this.channel = future.channel();
    } else {
      logger.warning("Piezo client failed to connect to " + remoteAddress.toString());
      throw new RuntimeException(future.cause());
    }
  }

  @Override
  public <O extends Message> ListenableFuture<O> encodeMethodCall(ClientMethod<O> method,
      Message input) {
    final JsonResponseFuture<O> responseFuture =
        handler.newProvisionalResponse(method.outputBuilder());

    JsonObject request = new JsonObject();
    request.add("id", new JsonPrimitive(responseFuture.requestId()));
    request.add("method", new JsonPrimitive(DOT_JOINER.join(method.serviceName(), method.name())));
    JsonArray params = new JsonArray();
    params.add(Messages.toJson(input));
    request.add("params", params);

    ByteBuf requestBuffer = Unpooled.copiedBuffer(new Gson().toJson(request), Charsets.UTF_8);
    HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
        rpcPath, requestBuffer);
    httpRequest.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json");
    httpRequest.headers().set(HttpHeaders.Names.CONTENT_LENGTH, requestBuffer.readableBytes());

    channel.writeAndFlush(httpRequest).addListener(new GenericFutureListener<ChannelFuture>() {

      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          handler.finish(responseFuture.requestId());
          responseFuture.setException(future.cause());
        }
      }

    });

    return responseFuture;
  }
}
