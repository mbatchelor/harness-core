package software.wings.service.impl;

import static software.wings.beans.Activity.Builder.anActivity;
import static software.wings.beans.CommandUnit.ExecutionResult.FAILURE;
import static software.wings.beans.CommandUnit.ExecutionResult.SUCCESS;

import software.wings.beans.Activity;
import software.wings.beans.Command;
import software.wings.beans.CommandUnit;
import software.wings.beans.CommandUnit.ExecutionResult;
import software.wings.beans.ServiceInstance;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.CommandUnitExecutorService;
import software.wings.service.intfc.ServiceCommandExecutorService;

import java.util.List;
import javax.inject.Inject;

// TODO: Auto-generated Javadoc

/**
 * Created by anubhaw on 6/2/16.
 */
public class ServiceCommandExecutorServiceImpl implements ServiceCommandExecutorService {
  /**
   * The Activity service.
   */
  @Inject ActivityService activityService;
  /**
   * The Command unit executor service.
   */
  @Inject CommandUnitExecutorService commandUnitExecutorService;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ServiceCommandExecutorService#execute(software.wings.beans.ServiceInstance,
   * software.wings.beans.Command)
   */
  @Override
  public ExecutionResult execute(ServiceInstance serviceInstance, Command command) {
    Activity activity = getPersistedActivity(serviceInstance, command);
    List<CommandUnit> commandUnits = command.getCommandUnits();
    command.setExecutionResult(SUCCESS);
    for (CommandUnit commandUnit : commandUnits) {
      ExecutionResult executionResult =
          commandUnitExecutorService.execute(serviceInstance.getHost(), commandUnit, activity.getUuid());
      commandUnit.setExecutionResult(executionResult);
      if (executionResult.equals(FAILURE)) {
        command.setExecutionResult(FAILURE);
        break;
      }
    }
    return command.getExecutionResult();
  }

  private Activity getPersistedActivity(ServiceInstance serviceInstance, Command command) {
    Activity activity = anActivity()
                            .withAppId(serviceInstance.getAppId())
                            .withEnvironmentId(serviceInstance.getEnvId())
                            .withServiceTemplateId(serviceInstance.getServiceTemplate().getUuid())
                            .withServiceTemplateName(serviceInstance.getServiceTemplate().getName())
                            .withServiceId(serviceInstance.getServiceTemplate().getService().getUuid())
                            .withServiceName(serviceInstance.getServiceTemplate().getService().getName())
                            .withCommandName(command.getName())
                            .withCommandType(command.getCommandUnitType().name())
                            .withHostName(serviceInstance.getHost().getHostName())
                            .build();

    if (serviceInstance.getRelease() != null) {
      activity.setReleaseId(serviceInstance.getRelease().getUuid());
      activity.setReleaseName(serviceInstance.getRelease().getReleaseName());
    }
    if (serviceInstance.getArtifact() != null) {
      activity.setArtifactName(serviceInstance.getArtifact().getDisplayName());
    }
    activity = activityService.save(activity);
    return activity;
  }
}
