package io.harness.cdng.k8s;

import com.google.inject.Inject;

import io.harness.Task;
import io.harness.ambiance.Ambiance;
import io.harness.beans.DelegateTask;
import io.harness.cdng.common.AmbianceHelper;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.ResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.engine.outcomes.OutcomeService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.execution.status.Status;
import io.harness.facilitator.modes.task.TaskExecutable;
import io.harness.references.OutcomeRefObject;
import io.harness.state.Step;
import io.harness.state.StepType;
import io.harness.state.io.StepInputPackage;
import io.harness.state.io.StepParameters;
import io.harness.state.io.StepResponse;
import software.wings.beans.TaskType;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployRollbackTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.sm.states.k8s.K8sRollingDeployRollback;

import java.util.Map;

public class K8sRollingRollbackStep implements Step, TaskExecutable {
  public static final StepType STEP_TYPE = StepType.builder().type("K8S_ROLLBACK_ROLLING").build();

  @Inject OutcomeService outcomeService;
  @Inject K8sStepHelper k8sStepHelper;

  @Override
  public Task obtainTask(Ambiance ambiance, StepParameters stepParameters, StepInputPackage inputPackage) {
    K8sRollingRollbackStepParameters k8sRollingRollbackStepParameters =
        ((K8sRollingRollbackStepInfo) stepParameters).getK8sRollingRollback();

    K8sRollingOutcome k8sRollingOutcome =
        (K8sRollingOutcome) outcomeService.resolve(ambiance, OutcomeRefObject.builder().name("rollingOutcome").build());

    Infrastructure infrastructure =
        (Infrastructure) outcomeService.resolve(ambiance, OutcomeRefObject.builder().name("infrastructure").build());
    if (infrastructure == null) {
      throw new InvalidRequestException(
          "Infrastructure step need to run before k8s rolling step", WingsException.ADMIN);
    }

    K8sRollingDeployRollbackTaskParameters taskParameters =
        K8sRollingDeployRollbackTaskParameters.builder()
            .activityId(UUIDGenerator.generateUuid())
            .releaseName(k8sRollingOutcome.getReleaseName())
            .releaseNumber(k8sRollingOutcome.getReleaseNumber())
            .commandName(K8sRollingDeployRollback.K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME)
            .k8sTaskType(K8sTaskParameters.K8sTaskType.DEPLOYMENT_ROLLING_ROLLBACK)
            .timeoutIntervalInMin(k8sRollingRollbackStepParameters.getTimeout())
            .k8sClusterConfig(k8sStepHelper.getK8sClusterConfig(infrastructure))
            .accountId(AmbianceHelper.getAccountId(ambiance))
            .build();

    return DelegateTask.builder()
        .waitId(UUIDGenerator.generateUuid())
        .accountId(AmbianceHelper.getAccountId(ambiance))
        .data(TaskData.builder()
                  .async(true)
                  .timeout(k8sRollingRollbackStepParameters.getTimeout())
                  .taskType(TaskType.K8S_COMMAND_TASK.name())
                  .parameters(new Object[] {taskParameters})
                  .build())
        .build();
  }

  @Override
  public StepResponse handleTaskResult(
      Ambiance ambiance, StepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    K8sTaskExecutionResponse executionResponse = (K8sTaskExecutionResponse) responseDataMap.values().iterator().next();

    if (executionResponse.getCommandExecutionStatus() == CommandExecutionResult.CommandExecutionStatus.SUCCESS) {
      return StepResponse.builder().status(Status.SUCCEEDED).build();
    } else {
      return StepResponse.builder().status(Status.FAILED).build();
    }
  }
}
