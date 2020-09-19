package io.harness.cvng.dashboard.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import io.harness.CvNextGenTest;
import io.harness.beans.NGPageResponse;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.core.entities.AppDynamicsCVConfig;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.TimeSeriesService;
import io.harness.cvng.dashboard.beans.TimeSeriesMetricDataDTO;
import io.harness.cvng.dashboard.services.api.TimeSeriesDashboardService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TimeSeriesDashboardServiceImplTest extends CvNextGenTest {
  @Inject private TimeSeriesDashboardService timeSeriesDashboardService;

  private String projectIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private String accountId;
  @Inject private HPersistence hPersistence;

  @Mock private CVConfigService cvConfigService;
  @Mock private TimeSeriesService timeSeriesService;

  @Before
  public void setUp() throws Exception {
    projectIdentifier = generateUuid();
    serviceIdentifier = generateUuid();
    envIdentifier = generateUuid();
    accountId = generateUuid();

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(timeSeriesDashboardService, "cvConfigService", cvConfigService, true);
    FieldUtils.writeField(timeSeriesDashboardService, "timeSeriesService", timeSeriesService, true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetSortedMetricData() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    when(timeSeriesService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, false));
    List<String> cvConfigs = Arrays.asList(cvConfigId);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(accountId, envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(cvConfig));

    NGPageResponse<TimeSeriesMetricDataDTO> response =
        timeSeriesDashboardService.getSortedMetricData(accountId, projectIdentifier, generateUuid(), envIdentifier,
            serviceIdentifier, CVMonitoringCategory.PERFORMANCE, start.toEpochMilli(), end.toEpochMilli(), 0, 10);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetSortedAnomalousMetricData() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    when(timeSeriesService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, true));
    List<String> cvConfigs = Arrays.asList(cvConfigId);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(accountId, envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(cvConfig));

    NGPageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getSortedAnomalousMetricData(
        accountId, projectIdentifier, generateUuid(), envIdentifier, serviceIdentifier,
        CVMonitoringCategory.PERFORMANCE, start.toEpochMilli(), end.toEpochMilli(), 0, 10);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getContent().size()).isEqualTo(response.getPageSize());
    response.getContent().forEach(timeSeriesMetricDataDTO -> {
      assertThat(timeSeriesMetricDataDTO.getMetricDataList()).isNotEmpty();
      timeSeriesMetricDataDTO.getMetricDataList().forEach(metricData -> {
        assertThat(metricData.getRisk().name()).isNotEqualTo(TimeSeriesMetricDataDTO.TimeSeriesRisk.LOW_RISK.name());
      });
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetSortedAnomalousMetricData_validatePageResponse() throws Exception {
    Instant start = Instant.parse("2020-07-07T02:40:00.000Z");
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String cvConfigId = generateUuid();
    when(timeSeriesService.getTimeSeriesRecordsForConfigs(any(), any(), any(), anyBoolean()))
        .thenReturn(getTimeSeriesRecords(cvConfigId, true));
    List<String> cvConfigs = Arrays.asList(cvConfigId);
    AppDynamicsCVConfig cvConfig = new AppDynamicsCVConfig();
    cvConfig.setUuid(cvConfigId);
    when(cvConfigService.list(accountId, envIdentifier, serviceIdentifier, CVMonitoringCategory.PERFORMANCE))
        .thenReturn(Arrays.asList(cvConfig));

    NGPageResponse<TimeSeriesMetricDataDTO> response = timeSeriesDashboardService.getSortedAnomalousMetricData(
        accountId, projectIdentifier, generateUuid(), envIdentifier, serviceIdentifier,
        CVMonitoringCategory.PERFORMANCE, start.toEpochMilli(), end.toEpochMilli(), 2, 3);

    assertThat(response).isNotNull();
    assertThat(response.getContent()).isNotEmpty();
    assertThat(response.getPageCount()).isEqualTo(40);
    assertThat(response.getContent().size()).isEqualTo(3);
    response.getContent().forEach(timeSeriesMetricDataDTO -> {
      assertThat(timeSeriesMetricDataDTO.getMetricDataList()).isNotEmpty();
      timeSeriesMetricDataDTO.getMetricDataList().forEach(metricData -> {
        assertThat(metricData.getRisk().name()).isNotEqualTo(TimeSeriesMetricDataDTO.TimeSeriesRisk.LOW_RISK.name());
      });
    });
  }

  private List<TimeSeriesRecord> getTimeSeriesRecords(String cvConfigId, boolean anomalousOnly) throws Exception {
    File file = new File(getClass().getClassLoader().getResource("timeseries/timeseriesRecords.json").getFile());
    final Gson gson = new Gson();
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<List<TimeSeriesRecord>>() {}.getType();
      List<TimeSeriesRecord> timeSeriesMLAnalysisRecords = gson.fromJson(br, type);
      timeSeriesMLAnalysisRecords.forEach(timeSeriesMLAnalysisRecord -> {
        timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
        timeSeriesMLAnalysisRecord.setBucketStartTime(Instant.parse("2020-07-07T02:40:00.000Z"));
        timeSeriesMLAnalysisRecord.getTimeSeriesGroupValues().forEach(groupVal -> {
          Instant baseTime = Instant.parse("2020-07-07T02:40:00.000Z");
          Random random = new Random();
          groupVal.setTimeStamp(baseTime.plus(random.nextInt(4), ChronoUnit.MINUTES));
          if (anomalousOnly) {
            groupVal.setRiskScore(2);
          }
        });
      });
      return timeSeriesMLAnalysisRecords;
    }
  }
}