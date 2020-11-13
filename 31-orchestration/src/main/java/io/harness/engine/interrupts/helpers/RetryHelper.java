package io.harness.engine.interrupts.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.harness.LevelUtils;
import io.harness.ambiance.Ambiance;
import io.harness.ambiance.AmbianceUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.ExecutionEngineDispatcher;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.execution.Status;
import io.harness.serializer.KryoSerializer;
import io.harness.state.io.StepParameters;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@OwnedBy(CDC)
@Slf4j
public class RetryHelper {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private OrchestrationEngine engine;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private AmbianceUtils ambianceUtils;
  @Inject private KryoSerializer kryoSerializer;

  public void retryNodeExecution(String nodeExecutionId, StepParameters parameters) {
    NodeExecution nodeExecution = Preconditions.checkNotNull(nodeExecutionService.get(nodeExecutionId));
    PlanNode node = nodeExecution.getNode();
    String newUuid = generateUuid();
    Ambiance ambiance = ambianceUtils.cloneForFinish(nodeExecution.getAmbiance());
    ambiance.addLevel(LevelUtils.buildLevelFromPlanNode(newUuid, node));
    NodeExecution newNodeExecution = cloneForRetry(nodeExecution, parameters, newUuid, ambiance);
    NodeExecution savedNodeExecution = nodeExecutionService.save(newNodeExecution);
    nodeExecutionService.updateRelationShipsForRetryNode(nodeExecution.getUuid(), savedNodeExecution.getUuid());
    nodeExecutionService.markRetried(nodeExecution.getUuid());
    executorService.submit(ExecutionEngineDispatcher.builder().ambiance(ambiance).orchestrationEngine(engine).build());
  }

  private NodeExecution cloneForRetry(
      NodeExecution nodeExecution, StepParameters parameters, String newUuid, Ambiance ambiance) {
    PlanNode newPlanNode = nodeExecution.getNode();
    if (parameters != null) {
      newPlanNode = nodeExecution.getNode().cloneForRetry(parameters);
    }
    List<String> retryIds = isEmpty(nodeExecution.getRetryIds()) ? new ArrayList<>() : nodeExecution.getRetryIds();
    retryIds.add(0, nodeExecution.getUuid());
    return NodeExecution.builder()
        .uuid(newUuid)
        .ambiance(ambiance)
        .node(newPlanNode)
        .mode(null)
        .startTs(null)
        .endTs(null)
        .initialWaitDuration(null)
        .resolvedStepParameters(null)
        .notifyId(nodeExecution.getNotifyId())
        .parentId(nodeExecution.getParentId())
        .nextId(nodeExecution.getNextId())
        .previousId(nodeExecution.getPreviousId())
        .lastUpdatedAt(null)
        .version(null)
        .executableResponses(new ArrayList<>())
        .interruptHistories(nodeExecution.getInterruptHistories())
        .additionalInputs(new ArrayList<>())
        .failureInfo(null)
        .status(Status.QUEUED)
        .timeoutInstanceIds(new ArrayList<>())
        .timeoutDetails(null)
        .outcomeRefs(new ArrayList<>())
        .retryIds(retryIds)
        .oldRetry(false)
        .build();
  }
}
