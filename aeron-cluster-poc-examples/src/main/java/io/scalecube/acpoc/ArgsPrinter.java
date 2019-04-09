package io.scalecube.acpoc;

public class ArgsPrinter {

  /**
   * Main program runner.
   *
   * @param args arguments
   */
  public static void main(String[] args) {
    int staticNodesCnt = 3;
    int dynamicNodesCount = 2;

    String[] endpoints = clusterMembersEndpoints(staticNodesCnt + dynamicNodesCount);

    System.out.println("******* Dynamic nodes *********");
    System.out.println("aeron.cluster.members=");

    for (int i = staticNodesCnt; i < staticNodesCnt + dynamicNodesCount; i++) {
      System.out.println("DynamicNode[" + i + "]");
      System.out.println("aeron.cluster.nodes=\"" + endpoints[i] + "\"");
    }
    System.out.println("Cluster members status endpoints");
    System.out.println(
        "aeron.cluster.members.status.endpoints=\"" + clusterMembersStatusEndpoints(3) + "\"");

    System.out.println("******* Static nodes *********");
    System.out.println("aeron.cluster.members=\"" + clusterMembersString(staticNodesCnt) + "\"");
  }

  private static String[] clusterMembersEndpoints(final int maxMemberCount) {
    final String[] clusterMembersEndpoints = new String[maxMemberCount];

    for (int i = 0; i < maxMemberCount; i++) {
      clusterMembersEndpoints[i] =
          "localhost:2011"
              + i
              + ','
              + "localhost:2022"
              + i
              + ','
              + "localhost:2033"
              + i
              + ','
              + "localhost:2044"
              + i
              + ','
              + "localhost:801"
              + i;
    }

    return clusterMembersEndpoints;
  }

  private static String clusterMembersString(final int memberCount) {
    final StringBuilder builder = new StringBuilder();

    for (int i = 0; i < memberCount; i++) {
      builder
          .append(i)
          .append(',')
          .append("localhost:2011")
          .append(i)
          .append(',')
          .append("localhost:2022")
          .append(i)
          .append(',')
          .append("localhost:2033")
          .append(i)
          .append(',')
          .append("localhost:2044")
          .append(i)
          .append(',')
          .append("localhost:801")
          .append(i)
          .append('|');
    }

    builder.setLength(builder.length() - 1);

    return builder.toString();
  }

  private static String clusterMembersStatusEndpoints(final int staticMemberCount) {
    final StringBuilder builder = new StringBuilder();

    for (int i = 0; i < staticMemberCount; i++) {
      builder.append("localhost:2022").append(i).append(',');
    }

    builder.setLength(builder.length() - 1);

    return builder.toString();
  }
}
