package io.harness.pms.pipeline.service;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.ModuleType;
import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.FeatureNotSupportedException;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.pipeline.CommonStepInfo;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.rule.Owner;

import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.groovy.util.Maps;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineEnforcementServiceImplTest extends PipelineServiceTestBase {
  private static final String accountId = "ACCOUNT_ID";
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock EnforcementClientService enforcementClientService;
  @Mock CommonStepInfo commonStepInfo;
  @InjectMocks PipelineEnforcementServiceImpl pipelineEnforcementService;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetFeatureRestrictionMap() {
    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST1);
    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST1, true);
    Mockito.when(enforcementClientService.getAvailabilityMap(featureRestrictionNames, accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThat(pipelineEnforcementService.getFeatureRestrictionMap(accountId,
                   featureRestrictionNames.stream().map(FeatureRestrictionName::toString).collect(Collectors.toSet())))
        .isEqualTo(featureRestrictionNameBooleanMap);

    Mockito.verify(enforcementClientService).getAvailabilityMap(featureRestrictionNames, accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestriction() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(
        SdkStep.newBuilder()
            .setStepType(stepType)
            .setStepInfo(StepInfo.newBuilder().setFeatureRestrictionName(FeatureRestrictionName.TEST5.name()).build())
            .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);
    featureRestrictionNames.add(FeatureRestrictionName.DEPLOYMENTS);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap = Maps.of(FeatureRestrictionName.TEST1, true);
    Mockito.when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    Mockito.when(enforcementClientService.getAvailabilityMap(featureRestrictionNames, accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType));

    Mockito.verify(enforcementClientService).getAvailabilityMap(featureRestrictionNames, accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestrictionThrowsException() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(SdkStep.newBuilder()
                       .setStepType(stepType)
                       .setStepInfo(StepInfo.newBuilder()
                                        .setName("test 5")
                                        .setFeatureRestrictionName(FeatureRestrictionName.TEST5.name())
                                        .build())
                       .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);
    featureRestrictionNames.add(FeatureRestrictionName.DEPLOYMENTS);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap =
        Maps.of(FeatureRestrictionName.TEST5, false, FeatureRestrictionName.DEPLOYMENTS, false);
    Mockito.when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    Mockito.when(enforcementClientService.getAvailabilityMap(featureRestrictionNames, accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThatThrownBy(
        () -> pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType)))
        .isInstanceOf(FeatureNotSupportedException.class)
        .hasMessage(
            "Your current plan does not support the use of following steps: [test 5].You have exceeded max number of deployments.Please upgrade your plan.");

    Mockito.verify(enforcementClientService).getAvailabilityMap(featureRestrictionNames, accountId);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testValidatePipelineExecutionRestrictionThrowsExceptionOnlyDeployment() {
    Map<String, Set<SdkStep>> sdkSteps = new HashMap<>();
    Set<SdkStep> sdkStepSet = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("test").setStepCategory(StepCategory.STAGE).build();
    sdkStepSet.add(SdkStep.newBuilder()
                       .setStepType(stepType)
                       .setStepInfo(StepInfo.newBuilder()
                                        .setName("test 5")
                                        .setFeatureRestrictionName(FeatureRestrictionName.TEST5.name())
                                        .build())
                       .build());
    sdkSteps.put(ModuleType.CD.name(), sdkStepSet);

    Set<FeatureRestrictionName> featureRestrictionNames = Sets.newHashSet();
    featureRestrictionNames.add(FeatureRestrictionName.TEST5);
    featureRestrictionNames.add(FeatureRestrictionName.DEPLOYMENTS);

    Map<FeatureRestrictionName, Boolean> featureRestrictionNameBooleanMap =
        Maps.of(FeatureRestrictionName.TEST5, true, FeatureRestrictionName.DEPLOYMENTS, false);
    Mockito.when(pmsSdkInstanceService.getSdkSteps()).thenReturn(sdkSteps);
    Mockito.when(enforcementClientService.getAvailabilityMap(featureRestrictionNames, accountId))
        .thenReturn(featureRestrictionNameBooleanMap);

    assertThatThrownBy(
        () -> pipelineEnforcementService.validatePipelineExecutionRestriction(accountId, Sets.newHashSet(stepType)))
        .isInstanceOf(FeatureNotSupportedException.class)
        .hasMessage("You have exceeded max number of deployments.Please upgrade your plan.");

    Mockito.verify(enforcementClientService).getAvailabilityMap(featureRestrictionNames, accountId);
  }
}