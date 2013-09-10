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

import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.*;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.soliton.protobuf.*;

import java.net.URI;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Concrete {@link io.soliton.protobuf.Server} implementation surfacing the registered services
 * over an HTTP transport using the JSON-RPC protocol.
 *
 * @see <a href="http://json-rpc.org/">JSON-RPC</a>
 */
public class HttpJsonRpcServer implements Server {

  private static final Logger logger = Logger.getLogger(
      HttpJsonRpcServer.class.getCanonicalName());

  private final ServiceGroup services = new DefaultServiceGroup();
  private final int port;
  private final String path;

  private Channel channel;
  private EventLoopGroup parentGroup;
  private EventLoopGroup childGroup;

  public HttpJsonRpcServer(int port, String path) {
    this.port = port;
    this.path = path;
  }

  @Override
  public ServiceGroup serviceGroup() {
    return services;
  }

  /**
   * Starts this server.
   * <p/>
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
            pipeline.addLast("decoder", new HttpRequestDecoder());
            pipeline.addLast("encode", new HttpResponseEncoder());
            pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
            pipeline.addLast("json-rpc", new JsonRpcHandler());
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
   * <p/>
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

  private final class JsonRpcHandler extends SimpleChannelInboundHandler<HttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
      HttpContent content = (HttpContent) request;
      System.out.println(content.content().toString(Charsets.UTF_8));

      JsonElement root = new JsonParser().parse(content.content().toString(Charsets.UTF_8));
      JsonRpcRequest jsonRpcRequest = JsonRpcRequest.of(root);

      Service service = services.lookupByName(jsonRpcRequest.getService());
      if (service == null) {
        // fail
      }
      ServerMethod<? extends Message, ? extends Message> method = service.lookup(
          jsonRpcRequest.getMethod());
      invoke(method, jsonRpcRequest.getPayload(), jsonRpcRequest.getId(), ctx.channel());

//      ByteBuf responseBuffer = Unpooled.copiedBuffer("world\n", Charsets.UTF_8);
//      FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
//          HttpResponseStatus.OK, responseBuffer);
//      response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
//      response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, responseBuffer.readableBytes());
//      ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private boolean validateTransport(HttpRequest request, Channel channel) throws Exception {
      URI uri = new URI(request.getUri());
      JsonObject errorResponse = null;

      if (!uri.getPath().equals(path)) {
        errorResponse = JsonRpcResponse.error(HttpResponseStatus.NOT_FOUND);
      }

      if (!request.getMethod().equals(HttpMethod.POST)) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.METHOD_NOT_ALLOWED);
        response.headers().set(HttpHeaders.Names.ALLOW, HttpMethod.POST);
        channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
      }

      if (!request.headers().get(HttpHeaders.Names.CONTENT_TYPE).equals("application/json")) {
        channel.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE)).addListener(ChannelFutureListener.CLOSE);
      }

      if (!(request instanceof HttpContent)) {
        channel.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.BAD_REQUEST)).addListener(ChannelFutureListener.CLOSE);
      }

      if (errorResponse != null) {
        ByteBuf responseBody = Unpooled.copiedBuffer(
            new Gson().toJson(errorResponse).getBytes(Charsets.UTF_8));
        channel.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK, responseBody)).addListener(ChannelFutureListener.CLOSE);
      }
    }

    private <I extends Message, O extends Message> void invoke(
        ServerMethod<I, O> method, JsonObject payload, JsonElement id, Channel channel) {
      I request = (I) parseObject(method.inputBuilder(), payload);
      ListenableFuture<O> response = method.invoke(request);
      FutureCallback<O> callback = new JsonRpcCallback<>(id, channel);
      Futures.addCallback(response, callback);
    }

    private Message parseObject(Message.Builder builder, JsonObject input) {
      Descriptors.Descriptor descriptor = builder.getDescriptorForType();
      for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
        String protoName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entry.getKey());
        Descriptors.FieldDescriptor field = descriptor.findFieldByName(protoName);
        if (field == null) {
          // fail;
        }
        if (field.isRepeated()) {
          if (!entry.getValue().isJsonArray()) {
            // fail
          }
          JsonArray array = entry.getValue().getAsJsonArray();
          for (JsonElement item : array) {
            builder.addRepeatedField(field, parseField(field, item, builder));
          }
        } else {
          builder.setField(field, parseField(field, entry.getValue(), builder));
        }
      }
      return builder.build();
    }

    private Object parseField(Descriptors.FieldDescriptor field, JsonElement value,
        Message.Builder enclosingBuilder) {
      switch (field.getType()) {
        case DOUBLE:
          if (!value.isJsonPrimitive()) {
            // fail;
          }
          return value.getAsDouble();
        case FLOAT:
          if (!value.isJsonPrimitive()) {
            // fail;
          }
          return value.getAsFloat();
        case INT64:
        case UINT64:
        case FIXED64:
        case SINT64:
        case SFIXED64:
          if (!value.isJsonPrimitive()) {
            // fail
          }
          return value.getAsLong();
        case INT32:
        case UINT32:
        case FIXED32:
        case SINT32:
        case SFIXED32:
          if (!value.isJsonPrimitive()) {
            // fail
          }
          return value.getAsInt();
        case BOOL:
          if (!value.isJsonPrimitive()) {
            // fail
          }
          return value.getAsBoolean();
        case STRING:
          if (!value.isJsonPrimitive()) {
            // fail
          }
          return value.getAsString();
        case GROUP:
        case MESSAGE:
          if (!value.isJsonObject()) {
            // fail
          }
          return parseObject(enclosingBuilder.getFieldBuilder(field), value.getAsJsonObject());
        case BYTES:
          break;
        case ENUM:
          break;
      }
      return null;
    }

  }

  public static void main(String... args) {
    HttpJsonRpcServer server = new HttpJsonRpcServer(3000, "rpc");
    server.start();
  }
}
