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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Iterator;

public class JsonRpcRequestMarshaller {

  public JsonRpcRequest marshall(JsonElement root) {
    if (!root.isJsonObject()) {
      // error
    }
    JsonObject jsonRpcRequest = root.getAsJsonObject();
    JsonElement id = jsonRpcRequest.get("id");
    if (id == null) {
      // error
    }

    JsonElement methodNameElement = jsonRpcRequest.get("method");
    if (!methodNameElement.isJsonPrimitive()) {
      // error
    }

    JsonPrimitive methodName = methodNameElement.getAsJsonPrimitive();
    if (!methodName.isString()) {
      // error
    }

    JsonElement paramsElement = jsonRpcRequest.get("params");
    if (!paramsElement.isJsonArray()) {
      // error
    }

    JsonArray params = paramsElement.getAsJsonArray();
    if (params.size() != 1) {
      // error
    }

    JsonElement paramElement = params.get(0);
    if (!paramElement.isJsonObject()) {
      // error
    }

    JsonObject param = paramElement.getAsJsonObject();
    Iterator<String> serviceAndMethod = DOT.split(methodName.getAsString()).iterator();

    if (!serviceAndMethod.hasNext()) {
      // error
    }


    return new JsonRpcRequest();
  }
}
