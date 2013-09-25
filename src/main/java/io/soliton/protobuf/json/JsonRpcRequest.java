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

class JsonRpcRequest {

  private final String service;
  private final String method;
  private final JsonElement id;
  private final JsonObject parameter;

  public JsonRpcRequest(String service, String method, JsonElement id,
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
