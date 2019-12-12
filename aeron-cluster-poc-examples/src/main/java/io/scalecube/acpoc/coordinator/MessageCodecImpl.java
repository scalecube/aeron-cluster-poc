package io.scalecube.acpoc.coordinator;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import io.scalecube.cluster.transport.api.Message;
import io.scalecube.cluster.transport.api.MessageCodec;
import io.scalecube.services.ServiceEndpoint;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import reactor.core.Exceptions;

class MessageCodecImpl implements MessageCodec {

  static Object decode(ByteBuffer byteBuffer) {
    try {
      return DefaultObjectMapper.OBJECT_MAPPER.readValue(
          new ByteBufferBackedInputStream(byteBuffer), ServiceEndpoint.class);
    } catch (IOException e) {
      return null;
    }
  }

  static ByteBuffer encode(Object input) {
    ServiceEndpoint serviceEndpoint = (ServiceEndpoint) input;
    try {
      return ByteBuffer.wrap(
          DefaultObjectMapper.OBJECT_MAPPER
              .writeValueAsString(serviceEndpoint)
              .getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw Exceptions.propagate(e);
    }
  }

  @Override
  public Message deserialize(InputStream stream) throws Exception {
    return DefaultObjectMapper.OBJECT_MAPPER.readValue(stream, Message.class);
  }

  @Override
  public void serialize(Message message, OutputStream stream) throws Exception {
    DefaultObjectMapper.OBJECT_MAPPER.writeValue(stream, message);
  }
}
