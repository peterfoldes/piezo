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

import com.google.common.base.Splitter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Iterator;

public class JsonRpcRequest {

  private static final Splitter DOT = Splitter.on('.').trimResults().omitEmptyStrings();

  private final String service;
  private final String method;
  private final JsonElement id;
  private final JsonObject parameter;

  public static JsonRpcRequest of(JsonElement root) throws JsonRpcError {
    if (!root.isJsonObject()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Received payload is not a JSON Object");
    }
    JsonObject request = root.getAsJsonObject();
    JsonElement id = request.get("id");
    if (id == null) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Received request is missing 'id' property");
    }

    JsonElement methodNameElement = request.get("method");
    if (!methodNameElement.isJsonPrimitive()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Method name is not a JSON primitive");
    }

    JsonPrimitive methodName = methodNameElement.getAsJsonPrimitive();
    if (!methodName.isString()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Method name is not a string");
    }

    JsonElement paramsElement = request.get("params");
    if (!paramsElement.isJsonArray()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'params' property is not an array");
    }

    JsonArray params = paramsElement.getAsJsonArray();
    if (params.size() != 1) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'params' property is not an array");
    }

    JsonElement paramElement = params.get(0);
    if (!paramElement.isJsonObject()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "Parameter is not an object");
    }

    JsonObject parameter = paramElement.getAsJsonObject();
    Iterator<String> serviceAndMethod = DOT.split(methodName.getAsString()).iterator();

    if (!serviceAndMethod.hasNext()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'method' property is not properly formatted");
    }

    String service = serviceAndMethod.next();
    if (!serviceAndMethod.hasNext()) {
      throw JsonRpcError.error(HttpResponseStatus.BAD_REQUEST,
          "'method' property is not properly formatted");
    }

    String method = serviceAndMethod.next();
    return new JsonRpcRequest(service, method, id, parameter);
  }

  private JsonRpcRequest(String service, String method, JsonElement id,
      JsonObject parameter) {
    this.service = service;
    this.method = method;
    this.id = id;
    this.parameter = parameter;
  }

  public String service() {
    return service;
  }

  public String method() {
    return method;
  }

  public JsonElement id() {
    return id;
  }

  public JsonObject parameter() {
    return parameter;
  }
}
