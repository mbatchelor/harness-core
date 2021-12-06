package io.harness.delegate.task.citasks.awsvm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.awsvm.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.awsvm.VmTaskExecutionResponse;
import io.harness.delegate.task.citasks.CICleanupTaskHandler;
import io.harness.delegate.task.citasks.awsvm.helper.HttpHelper;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CIVmCleanupTaskHandler implements CICleanupTaskHandler {
  @NotNull private Type type = CICleanupTaskHandler.Type.VM;
  @Inject private HttpHelper httpHelper;

  @Override
  public Type getType() {
    return type;
  }

  public VmTaskExecutionResponse executeTaskInternal(CICleanupTaskParams ciCleanupTaskParams) {
    CIVmCleanupTaskParams params = (CIVmCleanupTaskParams) ciCleanupTaskParams;
    log.info("Received request to clean VM with stage runtime ID {}", params.getStageRuntimeId());
    return callRunnerForCleanup(params.getStageRuntimeId());
  }

  private VmTaskExecutionResponse callRunnerForCleanup(String stageExecutionId) {
    Map<String, String> params = new HashMap<>();
    params.put("stage_id", stageExecutionId);

    CommandExecutionStatus executionStatus = CommandExecutionStatus.FAILURE;
    String errMessage = "";
    try {
      Response<Void> response = httpHelper.cleanupStageWithRetries(params);
      if (response.isSuccessful()) {
        executionStatus = CommandExecutionStatus.SUCCESS;
      }
    } catch (Exception e) {
      log.error("Failed to destory VM in runner", e);
      errMessage = e.getMessage();
    }

    return VmTaskExecutionResponse.builder().errorMessage(errMessage).commandExecutionStatus(executionStatus).build();
  }
}