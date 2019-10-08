package software.wings.exception;

import static io.harness.eraro.ErrorCode.STATE_MACHINE_ISSUE;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class StateMachineIssueException extends WingsException {
  private static final String DETAILS_KEY = "details";

  public StateMachineIssueException(String details) {
    super(null, null, STATE_MACHINE_ISSUE, Level.ERROR, null, null);
    super.param(DETAILS_KEY, details);
  }
}
