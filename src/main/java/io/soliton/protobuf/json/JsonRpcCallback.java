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

import com.google.common.util.concurrent.FutureCallback;
import com.google.gson.JsonElement;
import com.google.protobuf.Message;
import io.netty.channel.Channel;

public class JsonRpcCallback<O extends Message> implements FutureCallback<O> {

  private final JsonElement id;
  private final Channel channel;

  public JsonRpcCallback(JsonElement id, Channel channel) {
    this.id = id;
    this.channel = channel;
  }

  @Override
  public void onSuccess(O result) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void onFailure(Throwable t) {
    //To change body of implemented methods use File | Settings | File Templates.
  }
}
