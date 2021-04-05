package io.harness.cvng.beans.newrelic;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.stackdriver.StackdriverDashboardRequest;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("NEWRELIC_APPS_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class NewRelicApplicationFetchRequest extends DataCollectionRequest<NewRelicConnectorDTO> {
  public static final String DSL = StackdriverDashboardRequest.readDSL(
      "newrelic-applications.datacollection", NewRelicApplicationFetchRequest.class);

  @Builder.Default private String filter = "";

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return NewRelicUtils.getBaseUrl(getConnectorConfigDTO());
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return NewRelicUtils.collectionHeaders(getConnectorConfigDTO());
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("filter", filter);
    return envVariables;
  }
}
