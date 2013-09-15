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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.handler.codec.http.HttpResponseStatus;

public class JsonRpcResponse {

  private final JsonObject body;

  static JsonRpcResponse error(HttpResponseStatus status) {
    return error(status, null);
  }

  static JsonRpcResponse error(HttpResponseStatus status, String message) {
    return error(status, message, null);
  }

  static JsonRpcResponse error(HttpResponseStatus status, String message, JsonElement id) {
    JsonObject error = new JsonObject();
    error.add("code", new JsonPrimitive(status.code()));
    error.add("message", new JsonPrimitive(message));
    error.add("id", id);
    JsonObject body = new JsonObject();
    body.add("error", error);
    return new JsonRpcResponse(body);
  }

  public static JsonRpcResponse success(JsonObject payload, JsonElement id) {
    return null;
  }

  private JsonRpcResponse(JsonObject body) {
    this.body = body;
  }

  public JsonObject body() {
    return body;
  }
}
