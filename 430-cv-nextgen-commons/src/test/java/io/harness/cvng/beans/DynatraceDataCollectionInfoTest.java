package io.harness.cvng.beans;

import static io.harness.cvng.beans.DynatraceDataCollectionInfo.ENTITY_ID_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.GROUP_NAME_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.METRICS_TO_VALIDATE_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.METRIC_NAME_PARAM;
import static io.harness.cvng.beans.DynatraceDataCollectionInfo.QUERY_SELECTOR_PARAM;
import static io.harness.rule.OwnerRule.PAVIC;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DynatraceDataCollectionInfoTest extends CategoryTest {
  private static final String SERVICE_ID = "SERVICE_ID";
  private static final String GROUP_NAME = "GROUP_NAME";

  private DynatraceDataCollectionInfo classUnderTest;

  @Before
  public void setup() throws IOException {
    classUnderTest = DynatraceDataCollectionInfo.builder().serviceId(SERVICE_ID).groupName(GROUP_NAME).build();
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetEnvVariablesFromMetricPack() {
    MetricPackDTO metricPackDTO =
        MetricPackDTO.builder()
            .metrics(Collections.singleton(
                (MetricPackDTO.MetricDefinitionDTO.builder().name("MOCK_METRIC_NAME").path("MOCK_PATH").build())))
            .build();
    classUnderTest.setMetricPack(metricPackDTO);
    Map<String, Object> metricPackMetricsEnvVariables =
        classUnderTest.getDslEnvVariables(DynatraceConnectorDTO.builder().build());

    assertThat(metricPackMetricsEnvVariables.get(ENTITY_ID_PARAM)).isEqualTo(SERVICE_ID);
    assertThat(metricPackMetricsEnvVariables.get(GROUP_NAME_PARAM)).isEqualTo(GROUP_NAME);
    List<Map<String, String>> metricsToValidate =
        (List<Map<String, String>>) metricPackMetricsEnvVariables.get(METRICS_TO_VALIDATE_PARAM);

    assertThat(metricsToValidate.size()).isEqualTo(1);
    assertThat(metricsToValidate.get(0).get(METRIC_NAME_PARAM)).isEqualTo("MOCK_METRIC_NAME");
    assertThat(metricsToValidate.get(0).get(QUERY_SELECTOR_PARAM)).isEqualTo("MOCK_PATH");
  }

  @Test
  @Owner(developers = PAVIC)
  @Category(UnitTests.class)
  public void testGetEnvVariablesFromCustomMetrics() {
    List<DynatraceDataCollectionInfo.MetricCollectionInfo> customMetrics =
        Arrays.asList(DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
                          .identifier("metric_1")
                          .metricName("Metric 1")
                          .metricSelector("mock_metric_selector_1")
                          .build(),
            DynatraceDataCollectionInfo.MetricCollectionInfo.builder()
                .identifier("metric_2")
                .metricName("Metric 2")
                .metricSelector("mock_metric_selector_2")
                .build());
    classUnderTest.setCustomMetrics(customMetrics);
    Map<String, Object> metricPackMetricsEnvVariables =
        classUnderTest.getDslEnvVariables(DynatraceConnectorDTO.builder().build());

    assertThat(metricPackMetricsEnvVariables.get(ENTITY_ID_PARAM)).isEqualTo(SERVICE_ID);
    assertThat(metricPackMetricsEnvVariables.get(GROUP_NAME_PARAM)).isEqualTo(GROUP_NAME);
    List<Map<String, String>> metricsToValidate =
        (List<Map<String, String>>) metricPackMetricsEnvVariables.get(METRICS_TO_VALIDATE_PARAM);

    assertThat(metricsToValidate.size()).isEqualTo(2);
    assertThat(metricsToValidate.get(0).get(METRIC_NAME_PARAM)).isEqualTo("Metric 1");
    assertThat(metricsToValidate.get(0).get(QUERY_SELECTOR_PARAM)).isEqualTo("mock_metric_selector_1");
    assertThat(metricsToValidate.get(1).get(METRIC_NAME_PARAM)).isEqualTo("Metric 2");
    assertThat(metricsToValidate.get(1).get(QUERY_SELECTOR_PARAM)).isEqualTo("mock_metric_selector_2");
  }
}
