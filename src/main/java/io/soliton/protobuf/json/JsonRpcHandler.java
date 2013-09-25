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
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.*;
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
import java.net.URISyntaxException;
import java.util.Iterator;

/**
 * Netty handler implementing the JSON RPC protocol over HTTP.
 */
final class JsonRpcHandler extends SimpleChannelInboundHandler<HttpRequest> {

  private static final Splitter DOT = Splitter.on('.').trimResults().omitEmptyStrings();

  private final Server server;
  private final String rpcPath;

  /**
   * Exhaustive constructor.
   *
   * @param server the server to which this handler is attached
   * @param rpcPath the HTTP endpoint path
   */
  public JsonRpcHandler(Server server, String rpcPath) {
    this.server = server;
    this.rpcPath = rpcPath;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
    HttpContent content = (HttpContent) request;

    JsonRpcResponse transportError = validateTransport(request, ctx.channel());
    if (transportError != null) {
      ByteBuf responseBody = Unpooled.copiedBuffer(
          new Gson().toJson(transportError.body()).getBytes(Charsets.UTF_8));
      HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBody);
      httpResponse.headers().set(
          HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
      ctx.channel().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
      return;
    }

    JsonRpcRequest jsonRpcRequest;
    try {
      jsonRpcRequest = marshallRequest(content);
    } catch (JsonRpcError error) {
      JsonRpcResponse response = error.response();
      ByteBuf responseBody = Unpooled.copiedBuffer(
          new Gson().toJson(response.body()).getBytes(Charsets.UTF_8));
      HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBody);
      httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
      ctx.channel().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
      return;
    }

    Service service = server.serviceGroup().lookupByName(jsonRpcRequest.service());
    if (service == null) {
      JsonRpcResponse response = JsonRpcResponse.error(HttpResponseStatus.BAD_REQUEST,
          "Unknown service: " + jsonRpcRequest.service());
      ByteBuf responseBody = Unpooled.copiedBuffer(
          new Gson().toJson(response.body()).getBytes(Charsets.UTF_8));
      HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBody);
      httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
      ctx.channel().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
      return;
    }

    ServerMethod<? extends Message, ? extends Message> method = service.lookup(
        jsonRpcRequest.method());

    if (method == null) {
      JsonRpcResponse response = JsonRpcResponse.error(HttpResponseStatus.BAD_REQUEST,
          "Unknown method: " + jsonRpcRequest.method());
      ByteBuf responseBody = Unpooled.copiedBuffer(
          new Gson().toJson(response.body()).getBytes(Charsets.UTF_8));
      HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBody);
      httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
      ctx.channel().writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
      return;
    }

    invoke(method, jsonRpcRequest.parameter(), jsonRpcRequest.id(), ctx.channel());
  }

  /**
   * In charge of marshalling and validating the incoming HTTP body
   * into an actionable JSON-RPC request.
   *
   * @param content the content of the received HTTP request
   *
   * @return the marshalled JSON RPC request.
   */
  private JsonRpcRequest marshallRequest(HttpContent content) throws JsonRpcError {
    JsonElement root = new JsonParser().parse(content.content().toString(Charsets.UTF_8));

    if (!root.isJsonObject()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Received payload is not a JSON Object");
    }

    JsonObject request = root.getAsJsonObject();
    JsonElement id = request.get("id");
    JsonElement methodNameElement = request.get("method");
    JsonElement paramsElement = request.get("params");

    if (id == null) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Received request is missing 'id' property");
    }

    if (methodNameElement == null) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Received request is missing 'method' propoerty");
    }

    if (paramsElement == null) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Received request is missing 'params' propoerty");
    }


    if (!methodNameElement.isJsonPrimitive()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Method name is not a JSON primitive");
    }

    JsonPrimitive methodName = methodNameElement.getAsJsonPrimitive();
    if (!methodName.isString()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Method name is not a string");
    }

    if (!paramsElement.isJsonArray()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'params' property is not an array");
    }

    JsonArray params = paramsElement.getAsJsonArray();
    if (params.size() != 1) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'params' property is not an array");
    }

    JsonElement paramElement = params.get(0);
    if (!paramElement.isJsonObject()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Parameter is not an object");
    }

    JsonObject parameter = paramElement.getAsJsonObject();
    Iterator<String> serviceAndMethod = DOT.split(methodName.getAsString()).iterator();

    if (!serviceAndMethod.hasNext()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'method' property is not properly formatted");
    }

    String service = serviceAndMethod.next();
    if (!serviceAndMethod.hasNext()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'method' property is not properly formatted");
    }

    String method = serviceAndMethod.next();
    return new JsonRpcRequest(service, method, id, parameter);
  }

  /**
   * In charge of validating all the transport-related aspects of the incoming
   * HTTP request.
   *
   * <p>In case of failure, this method will return {@code false} after having
   * taken care of responding to the requester.</p>
   *
   * <p>The checks include:</p>
   *
   * <ul>
   *   <li>that the request's path matches that of this handler;</li>
   *   <li>that the request's method is {@code POST};</li>
   *   <li>that the request's body is not empty;</li>
   *   <li>that the request's content-type is {@code application/json};</li>
   * </ul>
   *
   * @param request the received HTTP request
   * @param channel the channel on which the request was received
   * @return {@code null} if the request passes the transport checks, the
   *    response to return to the client otherwise
   * @throws URISyntaxException if the URI of the request cannot be parsed
   */
  private JsonRpcResponse validateTransport(HttpRequest request, Channel channel)
      throws URISyntaxException {
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

    return errorResponse;
  }

  /**
   * Actually invokes the server method.
   *
   * @param method the method to invoke
   * @param parameter the request's parameter
   * @param id the request's client-side identifier
   * @param channel the channel on which the request was received
   * @param <I> the method's input proto-type
   * @param <O> the method's output proto-type
   */
  private <I extends Message, O extends Message> void invoke(
      ServerMethod<I, O> method, JsonObject parameter, JsonElement id, Channel channel) {
    I request = (I) Messages.fromJson(method.inputBuilder(), parameter);
    ListenableFuture<O> response = method.invoke(request);
    FutureCallback<O> callback = new JsonRpcCallback<>(id, channel);
    Futures.addCallback(response, callback);
  }
}
