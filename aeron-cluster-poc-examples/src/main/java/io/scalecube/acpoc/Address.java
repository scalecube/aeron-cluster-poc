package io.scalecube.acpoc;

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
  public String toString() {
    return host + ':' + port;
  }
}
