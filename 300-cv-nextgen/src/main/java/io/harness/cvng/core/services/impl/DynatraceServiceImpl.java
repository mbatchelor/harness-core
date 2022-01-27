package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.beans.ThirdPartyApiResponseStatus;
import io.harness.cvng.beans.dynatrace.DynatraceMetricListRequest;
import io.harness.cvng.beans.dynatrace.DynatraceMetricPackValidationRequest;
import io.harness.cvng.beans.dynatrace.DynatraceSampleDataRequest;
import io.harness.cvng.beans.dynatrace.DynatraceServiceDetailsRequest;
import io.harness.cvng.beans.dynatrace.DynatraceServiceListRequest;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.MetricPackValidationResponse.MetricPackValidationResponseBuilder;
import io.harness.cvng.core.beans.MetricPackValidationResponse.MetricValidationResponse;
import io.harness.cvng.core.beans.TimeSeriesSampleDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceMetricDTO;
import io.harness.cvng.core.beans.dynatrace.DynatraceServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DynatraceService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DynatraceServiceImpl
    implements DynatraceService, DataCollectionRequestResultExtractor<DynatraceConnectorDTO> {
  @Inject private OnboardingService onboardingService;

  @Override
  public List<DynatraceServiceDTO> getAllServices(
      ProjectParams projectParams, String connectorIdentifier, String filter, String tracingId) {
    Instant now = Instant.now();
    DataCollectionRequest<DynatraceConnectorDTO> request =
        // we are getting active services in the last 6 months
        DynatraceServiceListRequest.builder()
            .from(now.minus(Duration.ofDays(6 * 30L)).toEpochMilli())
            .to(now.toEpochMilli())
            .type(DataCollectionRequestType.DYNATRACE_SERVICE_LIST_REQUEST)
            .build();

    Type type = new TypeToken<List<DynatraceServiceDTO>>() {}.getType();
    return performRequestAndGetDataResult(
        request, onboardingService, type, projectParams, connectorIdentifier, tracingId);
  }

  @Override
  public List<DynatraceMetricDTO> getAllMetrics(
      ProjectParams projectParams, String connectorIdentifier, String tracingId) {
    DataCollectionRequest<DynatraceConnectorDTO> request =
        DynatraceMetricListRequest.builder().type(DataCollectionRequestType.DYNATRACE_METRIC_LIST_REQUEST).build();

    Type type = new TypeToken<List<DynatraceMetricDTO>>() {}.getType();
    return performRequestAndGetDataResult(
        request, onboardingService, type, projectParams, connectorIdentifier, tracingId);
  }

  @Override
  public DynatraceServiceDTO getServiceDetails(
      ProjectParams projectParams, String connectorIdentifier, String serviceEntityId, String tracingId) {
    DataCollectionRequest<DynatraceConnectorDTO> request =
        DynatraceServiceDetailsRequest.builder()
            .serviceId(serviceEntityId)
            .type(DataCollectionRequestType.DYNATRACE_SERVICE_DETAILS_REQUEST)
            .build();

    Type type = new TypeToken<DynatraceServiceDTO>() {}.getType();
    return performRequestAndGetDataResult(
        request, onboardingService, type, projectParams, connectorIdentifier, tracingId);
  }

  @Override
  public Set<MetricPackValidationResponse> validateData(ProjectParams projectParams, String connectorIdentifier,
      List<String> serviceMethodsIds, List<MetricPackDTO> metricPacks, String tracingId) {
    Set<MetricPackValidationResponse> metricPackValidationResponses = new HashSet<>();
    metricPacks.forEach(metricPack -> {
      DataCollectionRequest<DynatraceConnectorDTO> request =
          DynatraceMetricPackValidationRequest.builder()
              .serviceMethodsIds(serviceMethodsIds)
              .metricPack(metricPack)
              .type(DataCollectionRequestType.DYNATRACE_VALIDATION_REQUEST)
              .build();

      Type type = new TypeToken<List<MetricValidationResponse>>() {}.getType();
      List<MetricValidationResponse> validationResponses = performRequestAndGetDataResult(
          request, onboardingService, type, projectParams, connectorIdentifier, tracingId);

      MetricPackValidationResponseBuilder metricPackValidationResponseBuilder =
          MetricPackValidationResponse.builder()
              .metricPackName(metricPack.getIdentifier())
              .metricValidationResponses(validationResponses);

      MetricPackValidationResponse metricPackValidationResponse =
          metricPackValidationResponseBuilder.overallStatus(ThirdPartyApiResponseStatus.SUCCESS).build();
      metricPackValidationResponse.updateStatus();
      metricPackValidationResponses.add(metricPackValidationResponse);
    });

    return metricPackValidationResponses;
  }

  @Override
  public List<TimeSeriesSampleDTO> fetchSampleData(ProjectParams projectParams, String connectorIdentifier,
      String serviceId, String metricSelector, String tracingId) {
    Instant now = Instant.now();
    DataCollectionRequest<DynatraceConnectorDTO> request =
        DynatraceSampleDataRequest.builder()
            .from(now.minus(Duration.ofMinutes(60)).toEpochMilli())
            .to(now.toEpochMilli())
            .serviceId(serviceId)
            .metricSelector(metricSelector)
            .type(DataCollectionRequestType.DYNATRACE_SAMPLE_DATA_REQUEST)
            .build();

    Type type = new TypeToken<List<TimeSeriesSampleDTO>>() {}.getType();
    return performRequestAndGetDataResult(
        request, onboardingService, type, projectParams, connectorIdentifier, tracingId);
  }

  @Override
  public void checkConnectivity(
      String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier, String tracingId) {
    getAllServices(ProjectParams.builder()
                       .accountIdentifier(accountId)
                       .orgIdentifier(orgIdentifier)
                       .projectIdentifier(projectIdentifier)
                       .build(),
        connectorIdentifier, null, tracingId);
  }
}
