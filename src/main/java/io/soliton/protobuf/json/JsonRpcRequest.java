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

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.protobuf.Message;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.soliton.protobuf.ServerMethod;
import io.soliton.protobuf.Service;
import io.soliton.protobuf.ServiceGroup;

/**
 * Internal representation of a resolved JSON-RPC request.
 */
class JsonRpcRequest {

  private final String service;
  private final String method;
  private final JsonElement id;
  private final JsonObject parameter;

  /**
   * Exhaustive constructor
   *
   * @param service the service this call is targeting
   * @param method the method this call is targeting
   * @param id the generic identifier of the request, as set by the client
   * @param parameter the sole parameter of this call
   */
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

  /**
   * Executes this request asynchronously.
   *
   * @param services the context of defined services in which this request is
   *    executing
   * @return a handle on the future result of the invocation
   */
  public ListenableFuture<JsonRpcResponse> invoke(ServiceGroup services) {
    Service service = services.lookupByName(service());
    if (service == null) {
      JsonRpcResponse response = JsonRpcResponse.error(HttpResponseStatus.BAD_REQUEST,
          "Unknown service: " + service());
      return Futures.immediateFuture(response);
    }

    ServerMethod<? extends Message, ? extends Message> method = service.lookup(method());

    if (method == null) {
      JsonRpcResponse response = JsonRpcResponse.error(HttpResponseStatus.BAD_REQUEST,
          "Unknown method: " + method());
      return Futures.immediateFuture(response);
    }

    return invoke(method, parameter(), id());
  }

  /**
   * Actually invokes the server method.
   *
   * @param method the method to invoke
   * @param parameter the request's parameter
   * @param id the request's client-side identifier
   * @param <I> the method's input proto-type
   * @param <O> the method's output proto-type
   */
  private <I extends Message, O extends Message> ListenableFuture<JsonRpcResponse> invoke(
      ServerMethod<I, O> method, JsonObject parameter, JsonElement id) {
    I request = (I) Messages.fromJson(method.inputBuilder(), parameter);
    ListenableFuture<O> response = method.invoke(request);
    return Futures.transform(response, new JsonConverter());
  }

  private class JsonConverter implements Function<Message, JsonRpcResponse> {

    @Override
    public JsonRpcResponse apply(Message output) {
      return JsonRpcResponse.success(Messages.toJson(output), id());
    }
  }
}
