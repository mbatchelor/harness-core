package io.harness.cvng.core.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DatadogMetricHealthDefinition {
  String dashboardId;
  String dashboardName;
  String query;
  String groupingQuery;
  String metricName;
  String metric;
  String aggregation;
  String serviceInstanceIdentifierTag;
  List<String> metricTags;
  boolean isManualQuery;
  RiskProfile riskProfile;

  @JsonProperty(value = "isManualQuery")
  public boolean isManualQuery() {
    return isManualQuery;
  }
}