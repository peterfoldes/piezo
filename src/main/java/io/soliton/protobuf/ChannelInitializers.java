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

package io.soliton.protobuf;

import com.google.protobuf.Message;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;

/**
 * Utility methods pertaining to {@link ChannelInitializer}s.
 */
public class ChannelInitializers {

  public static final <M extends Message> ChannelInitializer<Channel> protoBuf(
      final M defaultInstance, final SimpleChannelInboundHandler<M> handler) {
    return new ChannelInitializer<Channel>() {

      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast("frameDecoder",
            new LengthFieldBasedFrameDecoder(10 * 1024 * 1024, 0, 4, 0, 4));
        channel.pipeline().addLast("protobufDecoder",
            new ProtobufDecoder(defaultInstance));
        channel.pipeline().addLast("frameEncoder", new LengthFieldPrepender(4));
        channel.pipeline().addLast("protobufEncoder", new ProtobufEncoder());
        channel.pipeline().addLast("piezoServerTransport", handler);
      }
    };
  }

  public static final ChannelInitializer<Channel> httpServer(
      final SimpleChannelInboundHandler<HttpRequest> handler) {
    return new ChannelInitializer<Channel>() {

      @Override
      protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("http-codec", new HttpServerCodec());
        pipeline.addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024));
        pipeline.addLast("json-rpc-server", handler);
      }
    };
  }

  public static final ChannelInitializer<Channel> httpClient(
      final SimpleChannelInboundHandler<HttpResponse> handler) {
    return new ChannelInitializer<Channel>() {

      @Override
      protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("http-codec", new HttpClientCodec());
        pipeline.addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024));
        pipeline.addLast("json-rpc-client", handler);
      }
    };
  }
}
