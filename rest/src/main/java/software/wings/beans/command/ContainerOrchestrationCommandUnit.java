package software.wings.beans.command;

import com.google.inject.Inject;

import org.mongodb.morphia.annotations.Transient;
import software.wings.api.DeploymentType;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.cloudprovider.gke.KubernetesContainerService;

/**
 * Created by peeyushaggarwal on 2/1/17.
 */
public abstract class ContainerOrchestrationCommandUnit extends AbstractCommandUnit {
  @Inject @Transient protected transient AwsClusterService awsClusterService;

  @Inject @Transient protected transient KubernetesContainerService kubernetesContainerService;

  /**
   * Instantiates a new command unit.
   *
   * @param commandUnitType the command unit type
   */
  public ContainerOrchestrationCommandUnit(CommandUnitType commandUnitType) {
    super(commandUnitType);
    setArtifactNeeded(true);
  }
}
