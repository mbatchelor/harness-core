package software.wings.delegatetasks.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BOJANA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.RELEASE_NAME;
import static software.wings.utils.WingsTestConstants.TIMEOUT_INTERVAL;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.k8s.K8sGlobalConfigService;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.k8s.taskhandler.K8sTaskHandler;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class K8sTaskTest extends WingsBaseTest {
  @Mock private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;
  @Mock private K8sTaskHandler k8sTaskHandler;
  @Mock private Map<String, K8sTaskHandler> k8sCommandTaskTypeToTaskHandlerMap;

  private K8sClusterConfig k8sClusterConfig;

  @InjectMocks
  private K8sTask k8sTask = new K8sTask(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(), null,
      mock(Consumer.class), mock(BooleanSupplier.class));

  private K8sTaskParameters k8sTaskParameters;

  @Before
  public void setup() {
    k8sClusterConfig = K8sClusterConfig.builder().build();
    k8sTaskParameters =
        new K8sTaskParameters(ACCOUNT_ID, APP_ID, COMMAND_NAME, ACTIVITY_ID, k8sClusterConfig, WORKFLOW_EXECUTION_ID,
            RELEASE_NAME, TIMEOUT_INTERVAL, K8sTaskType.INSTANCE_SYNC, HelmVersion.V2, null, false, false, true);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRunInstanceSync() {
    when(k8sCommandTaskTypeToTaskHandlerMap.get(K8sTaskType.INSTANCE_SYNC.name())).thenReturn(k8sTaskHandler);
    k8sTask.run(k8sTaskParameters);
    verify(k8sTaskHandler, times(1)).executeTask(k8sTaskParameters, null);
    verify(k8sTaskHandler, times(1)).executeTask(k8sTaskParameters, null);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRunInstanceSyncException() {
    when(k8sCommandTaskTypeToTaskHandlerMap.get(K8sTaskType.INSTANCE_SYNC.name())).thenReturn(null);
    K8sTaskExecutionResponse k8sTaskExecutionResponse = k8sTask.run(k8sTaskParameters);
    assertThat(k8sTaskExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRun() {
    k8sTaskParameters.setCommandType(K8sTaskType.APPLY);
    String kubeconfigFileContent = "kubeconfigFileContent";
    when(containerDeploymentDelegateHelper.getKubeconfigFileContent(any(K8sClusterConfig.class)))
        .thenReturn(kubeconfigFileContent);
    when(k8sCommandTaskTypeToTaskHandlerMap.get(K8sTaskType.APPLY.name())).thenReturn(k8sTaskHandler);
    k8sTask.run(k8sTaskParameters);
    verify(containerDeploymentDelegateHelper, times(1)).getKubeconfigFileContent(k8sClusterConfig);
    verify(k8sCommandTaskTypeToTaskHandlerMap, times(1)).get(K8sTaskType.APPLY.name());
    verify(k8sTaskHandler, times(1)).executeTask(any(K8sTaskParameters.class), any(K8sDelegateTaskParams.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRunException() {
    k8sTaskParameters.setCommandType(K8sTaskType.APPLY);
    k8sGlobalConfigService = null;
    K8sTaskExecutionResponse response = k8sTask.run(k8sTaskParameters);
    verify(containerDeploymentDelegateHelper, times(1)).getKubeconfigFileContent(k8sClusterConfig);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
