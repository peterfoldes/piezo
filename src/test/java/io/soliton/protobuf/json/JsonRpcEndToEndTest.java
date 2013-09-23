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

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.*;
import io.soliton.protobuf.Service;
import io.soliton.protobuf.testing.TestingSingleFile;
import io.soliton.protobuf.testing.TimeRequest;
import io.soliton.protobuf.testing.TimeResponse;
import io.soliton.protobuf.testing.TimeService;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;

public class JsonRpcEndToEndTest {

  private static HttpJsonRpcServer server;

  private static final class TimeServer implements TimeService.Interface {

    @Override
    public ListenableFuture<TimeResponse> getTime(TimeRequest request) {
      DateTimeZone timeZone = DateTimeZone.forID(request.getTimezone());
      DateTime now = new DateTime(timeZone);
      TimeResponse.Builder response = TimeResponse.newBuilder();
      return Futures.immediateFuture(response.setTime(now.getMillis()).build());
    }
  }

  private static final class DnsServer implements TestingSingleFile.Dns.Interface {

    @Override
    public ListenableFuture<TestingSingleFile.DnsResponse> resolve(
        TestingSingleFile.DnsRequest request) {
      TestingSingleFile.DnsResponse response = TestingSingleFile.DnsResponse.newBuilder()
          .setIpAddress(1234567).build();
      return Futures.immediateFuture(response);
    }
  }

  @BeforeClass
  public static void setUp() {
    server = new HttpJsonRpcServer(10000, "/rpc");
    Service timeService = TimeService.newService(new TimeServer());
    Service dnsService = TestingSingleFile.Dns.newService(new DnsServer());
    server.serviceGroup().addService(timeService);
    server.serviceGroup().addService(dnsService);
    server.start();
  }

  @AfterClass
  public static void tearDown() {
    server.stop();
  }

  @Test
  public void testRequestResponseMultiFile() throws Exception {
    JsonObject request = new JsonObject();
    request.addProperty("method", "TimeService.GetTime");
    request.addProperty("id", "identifier");
    JsonObject parameter = new JsonObject();
    parameter.addProperty("timezone", DateTimeZone.UTC.getID());
    JsonArray parameters = new JsonArray();
    parameters.add(parameter);
    request.add("params", parameters);

    HttpContent httpContent = new ByteArrayContent("application/json",
        new Gson().toJson(request).getBytes(Charsets.UTF_8));

    GenericUrl url = new GenericUrl();
    url.setScheme("http");
    url.setHost("localhost");
    url.setPort(10000);
    url.setRawPath("/rpc");

    HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
    HttpRequest httpRequest = requestFactory.buildPostRequest(url, httpContent);

    HttpResponse httpResponse = httpRequest.execute();
    Assert.assertEquals(HttpStatusCodes.STATUS_CODE_OK, httpResponse.getStatusCode());
    Reader reader = new InputStreamReader(httpResponse.getContent(), Charsets.UTF_8);
    JsonElement response = new JsonParser().parse(reader);
    Assert.assertTrue(response.isJsonObject());
    JsonObject responseObject = response.getAsJsonObject();
    Assert.assertNotNull(responseObject.get("id"));
    Assert.assertNotNull(responseObject.get("result"));
    JsonElement result = responseObject.get("result");
    Assert.assertTrue(result.isJsonObject());
    JsonObject resultObject = result.getAsJsonObject();
    Assert.assertNotNull(resultObject.get("time"));
  }

  @Test
  public void testRequestResponseSingleFile() throws InterruptedException {

//    TestingSingleFile.Dns.Interface client = TestingSingleFile.Dns.newStub(
//        new TcpClient(HostAndPort.fromParts("localhost", 10000)));
//    TestingSingleFile.DnsRequest request = TestingSingleFile.DnsRequest.newBuilder()
//        .setDomain("Castro.local").build();
//
//    final CountDownLatch latch = new CountDownLatch(1);
//    Futures.addCallback(client.resolve(request), new FutureCallback<TestingSingleFile.DnsResponse>() {
//      @Override
//      public void onSuccess(TestingSingleFile.DnsResponse result) {
//        Assert.assertEquals(1234567, result.getIpAddress());
//        latch.countDown();
//      }
//
//      @Override
//      public void onFailure(Throwable throwable) {
//        Throwables.propagate(throwable);
//        latch.countDown();
//      }
//    }, Executors.newCachedThreadPool());
//    latch.await();
  }
}
