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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.soliton.protobuf.Server;
import io.soliton.protobuf.ServerMethod;
import io.soliton.protobuf.Service;

import java.net.URI;

final class JsonRpcHandler extends SimpleChannelInboundHandler<HttpRequest> {

  private final Server server;
  private final String rpcPath;

  public JsonRpcHandler(Server server, String rpcPath) {
    this.server = server;
    this.rpcPath = rpcPath;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
    HttpContent content = (HttpContent) request;
    System.out.println(content.content().toString(Charsets.UTF_8));

    if (!validateTransport(request, ctx.channel())) {
      return;
    }

    JsonRpcRequest jsonRpcRequest = marshallRequest(content, ctx.channel());
    if (jsonRpcRequest == null) {
      return;
    }

    Service service = server.serviceGroup().lookupByName(jsonRpcRequest.service());
    if (service == null) {
      // fail
    }
    ServerMethod<? extends Message, ? extends Message> method = service.lookup(
        jsonRpcRequest.method());
    invoke(method, jsonRpcRequest.parameter(), jsonRpcRequest.id(), ctx.channel());

//      ByteBuf responseBuffer = Unpooled.copiedBuffer("world\n", Charsets.UTF_8);
//      FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
//          HttpResponseStatus.OK, responseBuffer);
//      response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
//      response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, responseBuffer.readableBytes());
//      ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
  }

  private JsonRpcRequest marshallRequest(HttpContent content, Channel channel) {
    JsonElement root = new JsonParser().parse(content.content().toString(Charsets.UTF_8));
    JsonRpcRequest jsonRpcRequest = null;
    try {
      jsonRpcRequest = JsonRpcRequest.of(root);
    } catch (JsonRpcError jsonRpcError) {
      JsonRpcResponse response = jsonRpcError.response();
      ByteBuf responseBody = Unpooled.copiedBuffer(
          new Gson().toJson(response).getBytes(Charsets.UTF_8));
      channel.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBody)).addListener(ChannelFutureListener.CLOSE);
      return null;
    }
    return jsonRpcRequest;
  }

  private boolean validateTransport(HttpRequest request, Channel channel) throws Exception {
    URI uri = new URI(request.getUri());
    JsonRpcResponse errorResponse = null;

    if (!uri.getPath().equals(rpcPath)) {
      errorResponse = JsonRpcResponse.error(HttpResponseStatus.NOT_FOUND);
    }

    if (!request.getMethod().equals(HttpMethod.POST)) {
      errorResponse = JsonRpcResponse.error(HttpResponseStatus.METHOD_NOT_ALLOWED);
    }

    if (!request.headers().get(HttpHeaders.Names.CONTENT_TYPE).equals("application/json")) {
      errorResponse = JsonRpcResponse.error(HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    if (!(request instanceof HttpContent)) {
      errorResponse = JsonRpcResponse.error(HttpResponseStatus.BAD_REQUEST,
          "HTTP request was empty");
    }

    if (errorResponse != null) {
      ByteBuf responseBody = Unpooled.copiedBuffer(
          new Gson().toJson(errorResponse).getBytes(Charsets.UTF_8));
      channel.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBody)).addListener(ChannelFutureListener.CLOSE);
    }

    return errorResponse == null;
  }

  private <I extends Message, O extends Message> void invoke(
      ServerMethod<I, O> method, JsonObject payload, JsonElement id, Channel channel) {
    I request = (I) ProtoJsonUtils.fromJson(method.inputBuilder(), payload);
    ListenableFuture<O> response = method.invoke(request);
    FutureCallback<O> callback = new JsonRpcCallback<>(id, channel);
    Futures.addCallback(response, callback);
  }
}
