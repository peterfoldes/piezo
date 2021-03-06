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

package io.soliton.protobuf.plugin;

import io.soliton.protobuf.plugin.testing.TestingOneFile.Person;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Julien Silland (julien@soliton.io)
 */
public class ProtoFileHandlerTest {

  @Test
  public void testHandle() throws IOException {
    Descriptors.Descriptor personDescriptor = Person.getDescriptor();
    FileDescriptorProto protoFile = personDescriptor.getFile().toProto();
    TypeMap types = TypeMap.of(protoFile);
    ProtoFileHandler fileHandler = new ProtoFileHandler(types, new ByteArrayOutputStream());
    fileHandler.handle(protoFile);
  }
}
