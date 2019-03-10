package software.wings.delegatetasks.aws;

import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static software.wings.beans.DelegateTask.DEFAULT_ASYNC_CALL_TIMEOUT;

import io.harness.delegate.beans.TaskData;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.beans.DelegateTask;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.aws.model.AwsAmiRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceDeployRequest;
import software.wings.service.impl.aws.model.AwsAmiServiceSetupRequest;
import software.wings.service.impl.aws.model.AwsAmiSwitchRoutesRequest;
import software.wings.service.intfc.aws.delegate.AwsAmiHelperServiceDelegate;

public class AwsAmiAsyncTaskTest extends WingsBaseTest {
  @Mock private DelegateLogService mockDelegateLogService;
  @Mock private AwsAmiHelperServiceDelegate mockAwsAmiHelperServiceDelegate;

  @InjectMocks
  private AwsAmiAsyncTask task = (AwsAmiAsyncTask) TaskType.AWS_AMI_ASYNC_TASK.getDelegateRunnableTask("delegateid",
      DelegateTask.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).data(TaskData.builder().build()).build(),
      notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("delegateLogService", mockDelegateLogService);
    on(task).set("awsAmiHelperServiceDelegate", mockAwsAmiHelperServiceDelegate);
  }

  @Test
  public void testRun() {
    AwsAmiRequest request = AwsAmiServiceSetupRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).setUpAmiService(any(), any());
    request = AwsAmiServiceDeployRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).deployAmiService(any(), any());
    request = AwsAmiSwitchRoutesRequest.builder().rollback(false).build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).switchAmiRoutes(any(), any());
    request = AwsAmiSwitchRoutesRequest.builder().rollback(true).build();
    task.run(new Object[] {request});
    verify(mockAwsAmiHelperServiceDelegate).rollbackSwitchAmiRoutes(any(), any());
  }
}