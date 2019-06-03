package io.scalecube.acpoc;

import java.util.Objects;

public class Address {

  private final String host;
  private final int port;

  public Address(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public static Address create(String host, int port) {
    return new Address(host, port);
  }

  public String host() {
    return host;
  }

  public int port() {
    return port;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Address address = (Address) o;
    return port == address.port && host.equals(address.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(host, port);
  }

  @Override
  public String toString() {
    return host + ':' + port;
  }
}
