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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.*;

/**
 * Implements the logic executed upon a server method returning a result or
 * throwing an exception.
 *
 * @param <O> the type of the response
 */
public class JsonRpcCallback<O extends Message> implements FutureCallback<O> {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final JsonElement id;
  private final Channel channel;

  public JsonRpcCallback(JsonElement id, Channel channel) {
    this.id = id;
    this.channel = channel;
  }

  @Override
  public void onSuccess(O result) {
    JsonObject payload = Messages.toJson(result);
    JsonRpcResponse response = JsonRpcResponse.success(payload, id);
    ByteBuf responseBuffer = Unpooled.copiedBuffer(GSON.toJson(response.body()), Charsets.UTF_8);
    FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
          HttpResponseStatus.OK, responseBuffer);
    httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
    httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, responseBuffer.readableBytes());
    channel.writeAndFlush(httpResponse);
  }

  @Override
  public void onFailure(Throwable t) {
    JsonRpcResponse response = JsonRpcResponse.error(HttpResponseStatus.INTERNAL_SERVER_ERROR,
        t.getMessage(), id);
    ByteBuf responseBuffer = Unpooled.copiedBuffer(GSON.toJson(response.body()), Charsets.UTF_8);
    FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
        HttpResponseStatus.OK, responseBuffer);
    httpResponse.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
    httpResponse.headers().set(HttpHeaders.Names.CONTENT_LENGTH, responseBuffer.readableBytes());
    channel.writeAndFlush(httpResponse);
  }
}
