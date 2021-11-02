package ci.pipeline.execution;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.sweepingoutputs.AwsVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.executionplan.CIExecutionPlanTestHelper;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.stateutils.buildstate.ConnectorUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StageCleanupUtilityTest extends CIExecutionTestBase {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;

  @InjectMocks private StageCleanupUtility stageCleanupUtility;
  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                      "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                  .build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEventForRunning() throws IOException {
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .containerNames(new ArrayList<>())
                                    .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                                    .build())
                        .build());

    CICleanupTaskParams cik8CleanupTaskParams = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);

    assertThat(cik8CleanupTaskParams).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testBuildAndfetchAwsCleanUpParameters() throws IOException {
    String poolId = "test";
    String stageRuntimeId = "test";
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(AwsVmStageInfraDetails.builder().poolId(poolId).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    CICleanupTaskParams cleanupTaskParams = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);

    assertThat(cleanupTaskParams).isNotNull();
    assertEquals(cleanupTaskParams.getType(), CICleanupTaskParams.Type.AWS_VM);
  }
}