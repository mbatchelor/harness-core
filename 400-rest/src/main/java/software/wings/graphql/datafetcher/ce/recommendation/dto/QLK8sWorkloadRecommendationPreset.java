package software.wings.graphql.datafetcher.ce.recommendation.dto;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@OwnedBy(CE)
public class QLK8sWorkloadRecommendationPreset {
  Double cpuRequest;
  Double cpuLimit;
  Double memoryRequest;
  Double memoryLimit;
  Double safetyMargin;
  Long minCpuMilliCores;
  Long minMemoryBytes;
}
