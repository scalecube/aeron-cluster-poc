package io.scalecube.acpoc;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.AeronCluster.Context;

public class ClientTest {

  public static void main(String[] args) {
    AeronCluster aeronCluster = AeronCluster.connect(
        new Context()
            .aeronDirectoryName("aeron-custom")
            .egressListener(null)
            .ingressChannel("aeron:udp")
            .clusterMemberEndpoints("0=localhost:9010,1=localhost:9011,2=localhost:9012"));

    boolean succKeepaliv = aeronCluster.sendKeepAlive();
    System.out.println("SENT keepalive: " + succKeepaliv);
  }

}
