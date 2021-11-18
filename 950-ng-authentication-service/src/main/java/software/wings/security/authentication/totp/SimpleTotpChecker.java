package software.wings.security.authentication.totp;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.security.authentication.TotpChecker;

import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import java.security.GeneralSecurityException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.joda.time.DateTimeUtils;

public class SimpleTotpChecker<T extends SimpleTotpChecker.Request> implements TotpChecker<T> {
  @Override
  public boolean check(T request) {
    String totpSecret = request.getSecret();
    int code = request.getCode();
    final long currentTime = DateTimeUtils.currentTimeMillis();
    try {
      if (!TimeBasedOneTimePasswordUtil.validateCurrentNumber(
              totpSecret, code, 0, currentTime, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS)
          && !TimeBasedOneTimePasswordUtil.validateCurrentNumber(
              totpSecret, code, 0, currentTime - 10000, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS)
          && !TimeBasedOneTimePasswordUtil.validateCurrentNumber(
              totpSecret, code, 0, currentTime + 10000, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS)) {
        return false;
      }
    } catch (GeneralSecurityException e) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }
    return true;
  }

  @Getter
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Request {
    private final String secret;
    private final int code;
  }
}