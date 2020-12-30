package io.harness.beans.serializer;

import io.harness.yaml.core.timeout.Timeout;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TimeoutUtils {
  public long getTimeoutInSeconds(Timeout timeout, long defaultTimeoutInSeconds) {
    if (timeout == null) {
      return defaultTimeoutInSeconds;
    }
    long timeoutLong = timeout.getNumericValue() * timeout.getUnit().getCoefficient();
    return timeoutLong > 0 ? timeoutLong : defaultTimeoutInSeconds;
  }
}
