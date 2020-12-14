package io.harness.event;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.events.OrchestrationEventType.PLAN_EXECUTION_STATUS_UPDATE;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.beans.OrchestrationGraph;
import io.harness.cache.SpringMongoStore;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;
import io.harness.testlib.RealMongo;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PlanExecutionStatusUpdateEventHandlerTest extends OrchestrationVisualizationTestBase {
  @Inject private SpringMongoStore mongoStore;

  @Inject PlanExecutionService planExecutionService;
  @Inject GraphGenerationService graphGenerationService;
  @Inject PlanExecutionStatusUpdateEventHandler planExecutionStatusUpdateEventHandler;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  @RealMongo
  public void shouldUpdateGraphWithStatusAndEndTs() {
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(generateUuid())
                                      .startTs(System.currentTimeMillis())
                                      .endTs(System.currentTimeMillis())
                                      .status(Status.PAUSED)
                                      .build();
    planExecutionService.save(planExecution);

    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build())
                                   .eventType(PLAN_EXECUTION_STATUS_UPDATE)
                                   .build();

    OrchestrationGraph orchestrationGraph = OrchestrationGraph.builder()
                                                .rootNodeIds(Lists.newArrayList(generateUuid()))
                                                .status(Status.PAUSING)
                                                .startTs(planExecution.getStartTs())
                                                .planExecutionId(planExecution.getUuid())
                                                .cacheKey(planExecution.getUuid())
                                                .cacheContextOrder(System.currentTimeMillis())
                                                .build();
    mongoStore.upsert(orchestrationGraph, Duration.ofDays(10));

    planExecutionStatusUpdateEventHandler.handleEvent(event);

    Awaitility.await().atMost(2, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
      OrchestrationGraph graphInternal = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());
      return graphInternal.getStatus() == Status.PAUSED;
    });

    OrchestrationGraph updatedGraph = graphGenerationService.getCachedOrchestrationGraph(planExecution.getUuid());

    assertThat(updatedGraph).isNotNull();
    assertThat(updatedGraph.getPlanExecutionId()).isEqualTo(planExecution.getUuid());
    assertThat(updatedGraph.getStartTs()).isEqualTo(planExecution.getStartTs());
    assertThat(updatedGraph.getStatus()).isEqualTo(planExecution.getStatus());
  }
}
