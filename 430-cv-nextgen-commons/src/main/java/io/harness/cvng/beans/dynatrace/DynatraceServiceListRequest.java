package io.harness.cvng.beans.dynatrace;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("DYNATRACE_SERVICE_LIST_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
@EqualsAndHashCode(callSuper = true)
public class DynatraceServiceListRequest extends DynatraceRequest {
  private static final List<String> FIELDS = Collections.singletonList("toRelationships");
  private static final Long PAGE_SIZE = 500L;
  private static final String ENTITY_SELECTOR = "type(\"dt.entity.service\")";

  private static final String DSL =
      DataCollectionRequest.readDSL("dynatrace-service-list.datacollection", DynatraceServiceListRequest.class);

  long from;
  long to;
  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> commonEnvVariables = super.fetchDslEnvVariables();
    commonEnvVariables.put("entitySelector", ENTITY_SELECTOR);
    String fieldsParam = String.join(",", FIELDS);
    commonEnvVariables.put("fields", fieldsParam);
    commonEnvVariables.put("pageSize", PAGE_SIZE);
    commonEnvVariables.put("from", from);
    commonEnvVariables.put("to", to);
    return commonEnvVariables;
  }
}
