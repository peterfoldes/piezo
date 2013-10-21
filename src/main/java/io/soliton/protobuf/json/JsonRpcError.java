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

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.handler.codec.http.HttpResponseStatus;

public class JsonRpcError extends Exception {

  private final HttpResponseStatus status;
  private final String message;

  public JsonRpcError(HttpResponseStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public JsonObject toJson() {
    JsonObject error = new JsonObject();
    error.add("code", new JsonPrimitive(status.code()));
    error.addProperty("message", message);
    return error;
  }

  public static JsonRpcError fromJson(JsonObject error) {
    return null;
  }

  public HttpResponseStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
