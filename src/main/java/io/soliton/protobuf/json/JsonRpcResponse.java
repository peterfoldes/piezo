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

public class JsonRpcResponse {

  private final JsonElement id;
  private final JsonObject error;
  private final JsonObject result;

  static JsonRpcResponse error(JsonRpcError error) {
    return error(error, null);
  }

  static JsonRpcResponse error(JsonRpcError error, JsonElement id) {
    return new JsonRpcResponse(id, error.toJson(), null);
  }

  public static JsonRpcResponse success(JsonObject payload, JsonElement id) {
    return new JsonRpcResponse(id, null, payload);
  }

  private JsonRpcResponse(JsonElement id, JsonObject error, JsonObject result) {
    this.id = id;
    this.error = error;
    this.result = result;
  }

  public JsonObject toJson() {
    JsonObject body = new JsonObject();
    body.add(JsonRpcProtocol.ID, id());

    if (isError()) {
      body.add(JsonRpcProtocol.ERROR, error());
    } else {
      body.add(JsonRpcProtocol.RESULT, result());
    }
    return body;
  }

  public boolean isError() {
    return error != null;
  }

  public JsonElement id() {
    return id;
  }

  public JsonObject error() {
    return error;
  }

  public JsonObject result() {
    return result;
  }
}
