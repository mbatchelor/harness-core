package io.harness.cdng.pipeline.plancreators;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;

import io.harness.advisers.fail.OnFailAdviserParameters;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGBaseTest;
import io.harness.cdng.executionplan.ExecutionPlanCreatorRegistrar;
import io.harness.cdng.pipeline.CDPipeline;
import io.harness.cdng.pipeline.beans.RollbackNode;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildrenParameters;
import io.harness.executionplan.plancreator.beans.PlanCreatorConstants;
import io.harness.executionplan.service.ExecutionPlanCreatorService;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.rule.Owner;
import io.harness.state.core.section.chain.SectionChainStepParameters;
import io.harness.yaml.utils.YamlPipelineUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.utils.WingsTestConstants;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PipelinePlanTest extends CDNGBaseTest {
  @Inject ExecutionPlanCreatorRegistrar executionPlanCreatorRegistrar;
  @Inject private ExecutionPlanCreatorService executionPlanCreatorService;

  @Before
  public void setUp() {
    executionPlanCreatorRegistrar.register();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testPipelinePlanForGivenYaml() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("cdng/multiStagePipeline.yml");
    CDPipeline cdPipeline = YamlPipelineUtils.read(testFile, CDPipeline.class);
    final Plan planForPipeline =
        executionPlanCreatorService.createPlanForPipeline(cdPipeline, WingsTestConstants.ACCOUNT_ID);
    List<PlanNode> planNodes = planForPipeline.getNodes();
    List<PlanNode> pipelinePlanNodeList = getNodesByIdentifier(planNodes, "managerServiceDeployment");
    assertThat(pipelinePlanNodeList.size()).isEqualTo(1);

    List<PlanNode> qaStageList = getNodesByIdentifier(planNodes, "qaStage");
    assertThat(qaStageList.size()).isEqualTo(1);
    List<PlanNode> prodStageNodesList = getNodesByIdentifier(planNodes, "prodStage");
    assertThat(prodStageNodesList.size()).isEqualTo(1);

    List<PlanNode> serviceNodesList = getNodesByIdentifier(planNodes, "service");
    assertThat(serviceNodesList.size()).isEqualTo(2);
    List<PlanNode> artifactsNodesList = getNodesByIdentifier(planNodes, "artifacts");
    assertThat(artifactsNodesList.size()).isEqualTo(2);
    List<PlanNode> primaryArtifactNodesList = getNodesByIdentifier(planNodes, "primary");
    assertThat(primaryArtifactNodesList.size()).isEqualTo(2);
    List<PlanNode> infrastructureNodesList = getNodesByIdentifier(planNodes, "infrastructure");
    assertThat(infrastructureNodesList.size()).isEqualTo(2);
    List<PlanNode> manifestsNodesList = getNodesByIdentifier(planNodes, "MANIFESTS");
    assertThat(manifestsNodesList.size()).isEqualTo(2);

    List<PlanNode> executionNodesList = getNodesByIdentifier(planNodes, "execution");
    assertThat(executionNodesList.size()).isEqualTo(2);

    List<PlanNode> stepGroupNodesList = getNodesByIdentifier(planNodes, "StepGroup1");
    assertThat(stepGroupNodesList.size()).isEqualTo(1);

    List<PlanNode> httpStepNodesList = getNodesByIdentifier(planNodes, "httpStep1");
    assertThat(httpStepNodesList.size()).isEqualTo(1);

    List<PlanNode> stage1RollOutNodesList = getNodesByIdentifier(planNodes, "rolloutDeployment1");
    assertThat(stage1RollOutNodesList.size()).isEqualTo(1);
    List<PlanNode> stage2RollOutNodesList = getNodesByIdentifier(planNodes, "rolloutDeployment2");
    assertThat(stage2RollOutNodesList.size()).isEqualTo(1);
    List<PlanNode> stage2RollBackNodesList = getNodesByIdentifier(planNodes, "rollbackRolloutDeployment2");
    assertThat(stage2RollBackNodesList.size()).isEqualTo(1);
    List<PlanNode> stage1RollBackNodesList = getNodesByIdentifier(planNodes, "rollbackRolloutDeployment1");
    assertThat(stage1RollBackNodesList.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testRollbackPlan() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("cdng/rollbackPipeline.yaml");
    CDPipeline cdPipeline = YamlPipelineUtils.read(testFile, CDPipeline.class);
    final Plan planForPipeline =
        executionPlanCreatorService.createPlanForPipeline(cdPipeline, WingsTestConstants.ACCOUNT_ID);
    List<PlanNode> planNodes = planForPipeline.getNodes();

    // Stage1Node
    PlanNode stageNode = getNodesByIdentifier(planNodes, "managerDeploymentStage").get(0);
    assertThat(stageNode.getAdviserObtainments().size()).isEqualTo(1);

    // Advisor attached -> rollbackNode
    String advisorNodeId =
        ((OnFailAdviserParameters) stageNode.getAdviserObtainments().get(0).getParameters()).getNextNodeId();
    PlanNode rollbackPlanNode = getNodeByUUID(planNodes, advisorNodeId).get();
    assertThat(rollbackPlanNode.getIdentifier()).isEqualTo("managerDeploymentStageRollback");

    List<RollbackNode> childNodes =
        ((RollbackOptionalChildChainStepParameters) rollbackPlanNode.getStepParameters()).getChildNodes();
    assertThat(childNodes).hasSize(2);

    // First child -> Step Groups Rollback Node
    PlanNode stepGroupsRollbackNode = getNodeByUUID(planNodes, childNodes.get(0).getNodeId()).get();
    assertThat(stepGroupsRollbackNode.getIdentifier())
        .isEqualTo(PlanCreatorConstants.STEP_GROUPS_ROLLBACK_NODE_IDENTIFIER);
    assertThat(childNodes.get(0).getDependentNodeIdentifier())
        .isEqualTo(PlanCreatorConstants.STAGES_NODE_IDENTIFIER + ".managerDeploymentStage."
            + PlanCreatorConstants.EXECUTION_NODE_IDENTIFIER);

    // Second Child -> Execution Rollback Node
    PlanNode executionRollbackNode = getNodeByUUID(planNodes, childNodes.get(1).getNodeId()).get();
    assertThat(executionRollbackNode.getIdentifier())
        .isEqualTo(PlanCreatorConstants.EXECUTION_ROLLBACK_NODE_IDENTIFIER);
    assertThat(childNodes.get(1).getDependentNodeIdentifier())
        .isEqualTo(PlanCreatorConstants.STAGES_NODE_IDENTIFIER + ".managerDeploymentStage."
            + PlanCreatorConstants.EXECUTION_NODE_IDENTIFIER);

    // Step Groups Rollback Node Children
    List<RollbackNode> stepGroupsRollbackNodeChildren =
        ((RollbackOptionalChildChainStepParameters) stepGroupsRollbackNode.getStepParameters()).getChildNodes();
    assertThat(stepGroupsRollbackNodeChildren).hasSize(3);

    // Step Groups Rollback Node First Child -> Parallel Node
    RollbackNode parallelRollbackNode = stepGroupsRollbackNodeChildren.get(0);
    PlanNode parallelRollbackPlanNode = getNodeByUUID(planNodes, parallelRollbackNode.getNodeId()).get();
    assertThat(parallelRollbackPlanNode.getIdentifier())
        .isEqualTo(PlanCreatorConstants.PARALLEL_STEP_GROUPS_ROLLBACK_NODE_IDENTIFIER);
    assertThat(parallelRollbackNode.getDependentNodeIdentifier())
        .isEqualTo(PlanCreatorConstants.STAGES_NODE_IDENTIFIER + ".managerDeploymentStage."
            + PlanCreatorConstants.EXECUTION_NODE_IDENTIFIER + ".StepGroup4");

    List<RollbackNode> parallelNodeChildren =
        ((RollbackOptionalChildrenParameters) parallelRollbackPlanNode.getStepParameters()).getParallelNodes();
    assertThat(parallelNodeChildren).hasSize(1);
    assertThat(getNodeByUUID(planNodes, parallelNodeChildren.get(0).getNodeId()).get().getIdentifier())
        .isEqualTo("StepGroup4_rollback");
    assertThat(parallelNodeChildren.get(0).isShouldAlwaysRun()).isTrue();

    // Step Groups Rollback Node Second Child
    RollbackNode secondStepGroupRollbackNode = stepGroupsRollbackNodeChildren.get(1);
    assertThat(getNodeByUUID(planNodes, secondStepGroupRollbackNode.getNodeId()).get().getIdentifier())
        .isEqualTo("StepGroup2_rollback");
    assertThat(secondStepGroupRollbackNode.getDependentNodeIdentifier())
        .isEqualTo(PlanCreatorConstants.STAGES_NODE_IDENTIFIER + ".managerDeploymentStage."
            + PlanCreatorConstants.EXECUTION_NODE_IDENTIFIER + ".StepGroup2");

    // Step Groups Rollback Node Third Child
    RollbackNode thirdStepGroupRollbackNode = stepGroupsRollbackNodeChildren.get(2);
    assertThat(getNodeByUUID(planNodes, thirdStepGroupRollbackNode.getNodeId()).get().getIdentifier())
        .isEqualTo("StepGroup1_rollback");
    assertThat(thirdStepGroupRollbackNode.getDependentNodeIdentifier())
        .isEqualTo(PlanCreatorConstants.STAGES_NODE_IDENTIFIER + ".managerDeploymentStage."
            + PlanCreatorConstants.EXECUTION_NODE_IDENTIFIER + ".StepGroup1");

    // Execution Rollback Node Children
    List<String> executionRollbackNodeChildIds =
        ((SectionChainStepParameters) executionRollbackNode.getStepParameters()).getChildNodeIds();
    assertThat(executionRollbackNodeChildIds).hasSize(2);
  }

  List<PlanNode> getNodesByIdentifier(List<PlanNode> planNodes, String identifier) {
    return planNodes.stream()
        .filter(planNode -> planNode.getIdentifier().equals(identifier))
        .collect(Collectors.toList());
  }

  Optional<PlanNode> getNodeByUUID(List<PlanNode> planNodes, String uuid) {
    return planNodes.stream().filter(planNode -> planNode.getUuid().equals(uuid)).findFirst();
  }
}
