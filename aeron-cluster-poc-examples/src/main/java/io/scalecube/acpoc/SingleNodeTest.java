package io.scalecube.acpoc;

import poc.service.EchoService;

public class SingleNodeTest {

  public static final int N_DEFAULT = 1;

  public static void main(String[] args) {

    int nNodes = nodes(args);
    SingleNode[] nodes = new SingleNode[nNodes];

    for (int i = 0; i < nNodes; i++) {
      EchoService service = new EchoService();
      nodes[i] = new SingleNode(service);
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      for (int i = 0; i < nodes.length; i++)
        try {
          nodes[i].close();
        } catch (Exception ignored) {
        }
    }));
  }

  private static int nodes(String[] args) {
    if (args.length == 0) {
      return N_DEFAULT;
    }
    try {
      return Integer.parseInt(args[0]);
    } catch (Throwable th) {
      return N_DEFAULT;
    }
  }
}
