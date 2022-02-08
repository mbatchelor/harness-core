package io.harness.engine;

import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NodeDispatcher implements Runnable {
  Node node;
  Ambiance ambiance;
  PmsNodeExecutionMetadata metadata;
  OrchestrationEngine engine;

  @Override
  public void run() {
    engine.triggerNode(ambiance, node, metadata);
  }
}
