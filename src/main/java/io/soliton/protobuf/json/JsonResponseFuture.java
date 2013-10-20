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

import com.google.common.util.concurrent.AbstractFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.Message;
import io.soliton.protobuf.RpcException;

/**
 * Represents a handle on the result of the invocation of a method over
 * JSON-RPC.
 *
 * <p>This implementation is in charge of marshalling </p>
 *
 * @param <V> the type of this promise.
 */
public final class JsonResponseFuture<V> extends AbstractFuture<V> {

  private final long requestId;
  private final Message.Builder responseBuilder;

  public JsonResponseFuture(long requestId, Message.Builder responseBuilder) {
    this.requestId = requestId;
    this.responseBuilder = responseBuilder;
  }

  /**
   * Sets the
   *
   * @param response the JSON representation of the RPC response
   */
  public void setResponse(JsonObject response) {
    if (response.has("error")) {
      JsonElement errorElement = response.get("error");
      if (!errorElement.isJsonObject()) {
        setException(new RpcException("Unknown error"));
        return;
      } else {
        // handle structured error
      }
    } else if (response.has("result")) {
      JsonElement resultElement = response.get("result");
      if (!resultElement.isJsonObject()) {
        setException(new RpcException("RPC result is not JSON object. Request ID: " + requestId));
        return;
      }
      try {
        set((V) Messages.fromJson(responseBuilder, resultElement.getAsJsonObject()));
      } catch (Exception e) {
        setException(e);
      }
      return;
    } else {
      setException(new RpcException(
          "Response contains neither error nor result. Request ID: " + requestId));
      return;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean set(V value) {
    return super.set(value);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected boolean setException(Throwable throwable) {
    return super.setException(throwable);
  }

  public long requestId() {
    return requestId;
  }

}
