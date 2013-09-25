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

import com.google.gson.JsonElement;
import io.netty.handler.codec.http.HttpResponseStatus;

public class JsonRpcError extends Exception {

  private final JsonRpcResponse response;

  public static JsonRpcError error(HttpResponseStatus status, String message) {
    return new JsonRpcError(JsonRpcResponse.error(status, message));
  }

  public static JsonRpcError error(HttpResponseStatus status, String message, JsonElement id) {
    return new JsonRpcError(JsonRpcResponse.error(status, message, id));
  }

  private JsonRpcError(JsonRpcResponse response) {
    this.response = response;
  }

  public JsonRpcResponse response() {
    return response;
  }
}
