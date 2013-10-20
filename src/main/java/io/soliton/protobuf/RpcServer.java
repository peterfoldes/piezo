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

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class RpcServer extends AbstracRpcServer {

  protected RpcServer(int port) {
    super(port, NioServerSocketChannel.class, new NioEventLoopGroup(), new NioEventLoopGroup());
  }

  protected ChannelInitializer<? extends Channel> channelInitializer() {
    return ChannelInitializers.protoBuf(Envelope.getDefaultInstance(),
        new ServerRpcHandler(serviceGroup()));
  }
}
