package software.wings.delegatetasks.aws;

import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
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
import software.wings.service.impl.aws.model.AwsEcrGetAuthTokenRequest;
import software.wings.service.impl.aws.model.AwsEcrGetImageUrlRequest;
import software.wings.service.impl.aws.model.AwsEcrRequest;
import software.wings.service.intfc.aws.delegate.AwsEcrHelperServiceDelegate;

public class AwsEcrTaskTest extends WingsBaseTest {
  @Mock private AwsEcrHelperServiceDelegate mockEcrServiceDelegate;

  @InjectMocks
  private AwsEcrTask task = (AwsEcrTask) TaskType.AWS_ECR_TASK.getDelegateRunnableTask("delegateid",
      DelegateTask.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).data(TaskData.builder().build()).build(),
      notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(task).set("ecrServiceDelegate", mockEcrServiceDelegate);
  }

  @Test
  public void testRun() {
    AwsEcrRequest request = AwsEcrGetImageUrlRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEcrServiceDelegate).getEcrImageUrl(any(), anyList(), anyString(), anyString());
    request = AwsEcrGetAuthTokenRequest.builder().build();
    task.run(new Object[] {request});
    verify(mockEcrServiceDelegate).getAmazonEcrAuthToken(any(), anyList(), anyString(), anyString());
  }
}