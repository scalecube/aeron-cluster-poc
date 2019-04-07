package io.scalecube.acpoc;

import java.util.Arrays;

public class ConfUtils {

  public static String clusterMembersString(final int memberCount) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < memberCount; i++) {
      builder
          .append(i).append(',')
          .append("localhost:2011").append(i).append(',')
          .append("localhost:2022").append(i).append(',')
          .append("localhost:2033").append(i).append(',')
          .append("localhost:2044").append(i).append(',')
          .append("localhost:801").append(i).append('|');
    }
    builder.setLength(builder.length() - 1);
    return builder.toString();
  }

  public static String clientMemberEndpoints(final int memberCount) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < memberCount; i++) {
      builder
          .append(i).append('=')
          .append("localhost:2011").append(i).append(',');
    }
    builder.setLength(builder.length() - 1);

    return builder.toString();
  }

  public static String[] clusterMembersEndpoints(final int maxMemberCount) {
    final String[] clusterMembersEndpoints = new String[maxMemberCount];
    for (int i = 0; i < maxMemberCount; i++) {
      clusterMembersEndpoints[i] = "localhost:2011" + i + ',' +
          "localhost:2022" + i + ',' +
          "localhost:2033" + i + ',' +
          "localhost:2044" + i + ',' +
          "localhost:801" + i;
    }
    return clusterMembersEndpoints;
  }

  public static String clusterMembersStatusEndpoints(final int staticMemberCount) {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < staticMemberCount; i++) {
      builder.append("localhost:2022").append(i).append(',');
    }
    builder.setLength(builder.length() - 1);
    return builder.toString();
  }

  public static String printAllStats(int numNodes){
    final StringBuilder builder = new StringBuilder();
    builder
        .append("ClusterMembersString [").append(clusterMembersString(numNodes)).append("]\n")
        .append("ClientMemberEndpoints [").append(clientMemberEndpoints(numNodes)).append("]\n")
        .append("ClusterMembersEndpoints [").append(Arrays.toString(clusterMembersEndpoints(numNodes))).append("]\n")
        .append("ClusterMembersStatusEndpoints [").append(clusterMembersStatusEndpoints(numNodes)).append("]\n");
    return builder.toString();
}

  public static void main(String[] args) {
    System.out.println(printAllStats(3));
  }

}
