package io.scalecube.acpoc;

import java.io.File;
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
   * Creates tmp file with using the given value.
   *
   * @param value target.
   */
  public static void removeFile(String value) {
    IoUtil.delete(new File(value), true);
  }

  public static void checkInterruptedStatus() {
    if (Thread.currentThread().isInterrupted()) {
      fail("unexpected interrupt - test likely to have timed out");
    }
  }

  public static void fail(String reason) {
    throw new IllegalStateException(reason);
  }
}
