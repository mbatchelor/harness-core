package software.wings.delegatetasks.azure.arm.deployment;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static com.cronutils.utils.StringUtils.EMPTY;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.context.ARMDeploymentSteadyStateContext;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.AssignmentProvisioningState;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentDeploymentJob;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentDeploymentJobResult;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentJobCreatedResource;
import io.harness.azure.model.blueprint.assignment.operation.AssignmentOperation;
import io.harness.azure.model.blueprint.assignment.operation.AzureResourceManagerError;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import software.wings.delegatetasks.azure.arm.deployment.context.DeploymentBlueprintSteadyStateContext;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.resources.DeploymentOperationProperties;
import com.microsoft.azure.management.resources.implementation.DeploymentOperationInner;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class ARMDeploymentSteadyStateChecker {
  @Inject protected TimeLimiter timeLimiter;

  public void waitUntilCompleteWithTimeout(
      ARMDeploymentSteadyStateContext context, AzureManagementClient azureManagementClient, LogCallback logCallback) {
    try {
      Callable<Object> objectCallable = () -> {
        while (true) {
          String provisioningState = azureManagementClient.getARMDeploymentStatus(context);
          logCallback.saveExecutionLog(
              String.format("Deployment Status for - [%s] is [%s]", context.getDeploymentName(), provisioningState));

          if (isDeploymentComplete(provisioningState)) {
            String errorMessage = printProvisioningResults(context, azureManagementClient, logCallback);
            if (ARMDeploymentStatus.isSuccess(provisioningState)) {
              logCallback.saveExecutionLog(
                  format("%nARM Deployment - [%s] completed successfully", context.getDeploymentName()), INFO, SUCCESS);
              return Boolean.TRUE;
            } else {
              String message = format(
                  "%nARM Deployment failed for deployment - [%s]%n[%s]", context.getDeploymentName(), errorMessage);
              logCallback.saveExecutionLog(message, ERROR, FAILURE);
              throw new InvalidRequestException(message);
            }
          }
          sleep(ofSeconds(context.getStatusCheckIntervalInSeconds()));
        }
      };

      timeLimiter.callWithTimeout(objectCallable, context.getSteadyCheckTimeoutInMinutes(), TimeUnit.MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String message = format("Timed out waiting for executing operation deployment - [%s], %n %s",
          context.getDeploymentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, e);
    } catch (Exception e) {
      String message = format(
          "Error while waiting for executing operation [%s], %n %s", context.getDeploymentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, e);
    }
  }

  private String printProvisioningResults(
      ARMDeploymentSteadyStateContext context, AzureManagementClient azureManagementClient, LogCallback logCallback) {
    StringBuilder errorMessage = new StringBuilder("");
    logCallback.saveExecutionLog("");
    PagedList<DeploymentOperationInner> deploymentOperations = azureManagementClient.getDeploymentOperations(context);
    for (DeploymentOperationInner operation : deploymentOperations) {
      if (operation.properties() != null && operation.properties().targetResource() != null) {
        DeploymentOperationProperties properties = operation.properties();
        String resourceType = properties.targetResource().resourceType();
        String resourceName = properties.targetResource().resourceName();
        String resourceId = String.format("%s - %s", resourceType, resourceName);
        String status = isEmpty(properties.provisioningState()) ? "Not Available" : properties.provisioningState();
        logCallback.saveExecutionLog(String.format("%s :: [%s]", resourceId, status), hasFailed(status) ? ERROR : INFO);

        Object statusMessage = properties.statusMessage();
        if (hasFailed(status) && statusMessage != null) {
          errorMessage
              .append(String.format("Resource - [%s], %nFailed due to - [%s]", resourceId, statusMessage.toString()))
              .append("\n");
        }
      }
    }
    return errorMessage.toString();
  }

  private boolean hasFailed(String status) {
    return ARMDeploymentStatus.FAILED.getStatus().equalsIgnoreCase(status);
  }

  private boolean isDeploymentComplete(String provisioningState) {
    return (provisioningState == null) || ARMDeploymentStatus.SUCCEEDED.getStatus().equalsIgnoreCase(provisioningState)
        || ARMDeploymentStatus.FAILED.getStatus().equalsIgnoreCase(provisioningState)
        || ARMDeploymentStatus.CANCELLED.getStatus().equalsIgnoreCase(provisioningState);
  }

  private enum ARMDeploymentStatus {
    ACCEPTED("Accepted"),
    RUNNING("Running"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    private final String status;
    ARMDeploymentStatus(String status) {
      this.status = status;
    }
    public String getStatus() {
      return status;
    }
    public static boolean isSuccess(String status) {
      return SUCCEEDED.getStatus().equalsIgnoreCase(status);
    }
  }

  public void waitUntilCompleteWithTimeout(DeploymentBlueprintSteadyStateContext context,
      AzureBlueprintClient azureBlueprintClient, LogCallback logCallback) {
    try {
      Callable<Object> objectCallable = () -> {
        while (true) {
          String assignmentName = context.getAssignmentName();
          String assignmentProvisioningStatus = getAssignmentProvisioningStatus(context, azureBlueprintClient);
          logCallback.saveExecutionLog(
              String.format("Deployment Status for - [%s] is [%s]", assignmentName, assignmentProvisioningStatus));

          if (isDeploymentComplete(assignmentProvisioningStatus)) {
            printAssignmentOperations(context, azureBlueprintClient, logCallback);
            if (AssignmentProvisioningState.SUCCEEDED
                == AssignmentProvisioningState.fromString(assignmentProvisioningStatus)) {
              logCallback.saveExecutionLog(
                  format("%nBlueprint Deployment - [%s] completed successfully", assignmentName), INFO, SUCCESS);
              return Boolean.TRUE;
            } else {
              String message = format("%nBlueprint deployment failed - Assignment Name: [%s]", assignmentName);
              throw new InvalidRequestException(message);
            }
          }
          sleep(ofSeconds(context.getStatusCheckIntervalInSeconds()));
        }
      };

      timeLimiter.callWithTimeout(objectCallable, context.getSteadyCheckTimeoutInMinutes(), TimeUnit.MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String message = format("Timed out waiting for executing operation deployment - [%s], %n %s",
          context.getAssignmentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, e);
    } catch (InvalidRequestException e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, FAILURE);
      throw e;
    } catch (Exception e) {
      String message = format(
          "Error while waiting for executing operation [%s], %n %s", context.getAssignmentName(), e.getMessage());
      logCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, e);
    }
  }

  private String getAssignmentProvisioningStatus(
      DeploymentBlueprintSteadyStateContext context, AzureBlueprintClient azureBlueprintClient) {
    String assignmentName = context.getAssignmentName();
    String resourceScope = context.getAssignmentResourceScope();
    Assignment assignment = azureBlueprintClient.getAssignment(context.getAzureConfig(), resourceScope, assignmentName);

    if (assignment == null) {
      throw new InvalidRequestException(
          format("Assignment is null, assignmentName: %s, assignmentResourceScope: %s", assignmentName, resourceScope));
    }

    if (assignment.getProperties() == null) {
      throw new InvalidRequestException(
          format("Assignment properties are null, assignmentName: %s, assignmentResourceScope: %s", assignmentName,
              resourceScope));
    }

    return assignment.getProperties().getProvisioningState().getValue();
  }

  private void printAssignmentOperations(DeploymentBlueprintSteadyStateContext context,
      AzureBlueprintClient azureBlueprintClient, LogCallback logCallback) {
    PagedList<AssignmentOperation> assignmentOperations = azureBlueprintClient.listAssignmentOperations(
        context.getAzureConfig(), context.getAssignmentResourceScope(), context.getAssignmentName());
    logCallback.saveExecutionLog(format("%nDeployment Jobs:"));
    logAssignmentOperations(assignmentOperations, logCallback);
  }

  private void logAssignmentOperations(PagedList<AssignmentOperation> assignmentOperations, LogCallback logCallback) {
    assignmentOperations.stream()
        .map(AssignmentOperation::getProperties)
        .map(AssignmentOperation.Properties::getDeployments)
        .flatMap(Stream::of)
        .forEach(assignmentDeploymentJob -> logAssignmentDeploymentJob(assignmentDeploymentJob, logCallback));
  }

  private void logAssignmentDeploymentJob(final AssignmentDeploymentJob deploymentJob, final LogCallback logCallback) {
    AssignmentDeploymentJobResult deploymentJobResult = deploymentJob.getResult();
    if (deploymentJobResult == null) {
      logCallback.saveExecutionLog(format("%nDeployment Jobs list is empty"));
      return;
    }

    String resourceIds = getJobCreatedResourceIds(deploymentJobResult);
    AzureResourceManagerError deploymentError = deploymentJobResult.getError();
    String error = deploymentError != null ? deploymentError.getMessage() : EMPTY;
    logCallback.saveExecutionLog(format(
        "%n- Job Id: [%s]%n- Job Kind: [%s]%n- Job State: [%s]%n- Job Created Resource IDs: [%s]%n- Job Result Error: [%s]",
        deploymentJob.getJobId(), deploymentJob.getKind(), deploymentJob.getJobState(), resourceIds, error));
  }

  private String getJobCreatedResourceIds(AssignmentDeploymentJobResult deploymentJobResult) {
    if (deploymentJobResult.getResources() == null) {
      return EMPTY;
    }
    AssignmentJobCreatedResource[] resources = deploymentJobResult.getResources();
    StringJoiner resourceIds = new StringJoiner(",");
    for (AssignmentJobCreatedResource res : resources) {
      resourceIds.add(res.getId());
    }
    return resourceIds.toString();
  }
}
