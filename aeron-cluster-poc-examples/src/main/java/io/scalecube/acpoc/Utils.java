package io.scalecube.acpoc;

import java.util.UUID;
import org.agrona.IoUtil;

public class Utils {

  private Utils() {
    // no-op
  }

  /**
   * Creates tmp file with using the given value.
   *
   * @param value target.
   */
  public static String tmpFileName(String value) {
    return IoUtil.tmpDirName()
        + value
        + '-'
        + System.getProperty("user.name", "default")
        + '-'
        + UUID.randomUUID().toString();
  }

  /**
   * In order to let interrupt the process, thi method is regularly called in 'waiting' loops.
   */
  public static void checkInterruptedStatus() {
    if (Thread.currentThread().isInterrupted()) {
      fail("unexpected interrupt - test likely to have timed out");
    }
  }

  /**
   * Fail for a reason.
   *
   * @param reason to fail
   */
  public static void fail(String reason) {
    throw new IllegalStateException(reason);
  }
}
