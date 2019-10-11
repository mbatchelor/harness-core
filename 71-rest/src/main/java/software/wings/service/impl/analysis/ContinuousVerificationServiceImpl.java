package software.wings.service.impl.analysis;

import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.persistence.HQuery.excludeAuthority;
import static java.lang.Math.abs;
import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static java.util.Collections.emptySet;
import static software.wings.beans.Application.GLOBAL_APP_ID;
import static software.wings.beans.TaskType.CUSTOM_LOG_COLLECTION_TASK;
import static software.wings.beans.TaskType.ELK_COLLECT_LOG_DATA;
import static software.wings.beans.TaskType.STACKDRIVER_COLLECT_LOG_DATA;
import static software.wings.beans.TaskType.SUMO_COLLECT_LOG_DATA;
import static software.wings.beans.alert.AlertType.CONTINUOUS_VERIFICATION_ALERT;
import static software.wings.common.VerificationConstants.APPDYNAMICS_DEEPLINK_FORMAT;
import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
import static software.wings.common.VerificationConstants.CV_24x7_STATE_EXECUTION;
import static software.wings.common.VerificationConstants.DUMMY_HOST_NAME;
import static software.wings.common.VerificationConstants.ERROR_METRIC_NAMES;
import static software.wings.common.VerificationConstants.HEARTBEAT_METRIC_NAME;
import static software.wings.common.VerificationConstants.NEW_RELIC_DEEPLINK_FORMAT;
import static software.wings.common.VerificationConstants.PROMETHEUS_DEEPLINK_FORMAT;
import static software.wings.common.VerificationConstants.VERIFICATION_HOST_PLACEHOLDER;
import static software.wings.common.VerificationConstants.getLogAnalysisStates;
import static software.wings.common.VerificationConstants.getMetricAnalysisStates;
import static software.wings.resources.PrometheusResource.renderFetchQueries;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.sm.states.APMVerificationState.buildMetricInfoMap;
import static software.wings.sm.states.AbstractLogAnalysisState.HOST_BATCH_SIZE;
import static software.wings.sm.states.DatadogState.metricEndpointsInfo;
import static software.wings.verification.TimeSeriesDataPoint.initializeTimeSeriesDataPointsList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SortOrder.OrderType;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.time.Timestamp;
import io.harness.waiter.WaitNotifyEngine;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.mongodb.morphia.query.Query;
import software.wings.APMFetchConfig;
import software.wings.annotation.EncryptableSetting;
import software.wings.api.DeploymentType;
import software.wings.app.MainConfiguration;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.AwsConfig;
import software.wings.beans.BugsnagConfig;
import software.wings.beans.DatadogConfig;
import software.wings.beans.DynaTraceConfig;
import software.wings.beans.ElkConfig;
import software.wings.beans.FeatureName;
import software.wings.beans.GcpConfig;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.SumoConfig;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.TaskType;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.delegatetasks.DataCollectionExecutorService;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.metrics.TimeSeriesDataRecord.TimeSeriesMetricRecordKeys;
import software.wings.metrics.appdynamics.AppdynamicsConstants;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.ThirdPartyApiCallLog;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData.ContinuousVerificationExecutionMetaDataKeys;
import software.wings.service.impl.analysis.MetricAnalysisRecord.MetricAnalysisRecordKeys;
import software.wings.service.impl.analysis.TimeSeriesRiskSummary.TimeSeriesRiskSummaryKeys;
import software.wings.service.impl.analysis.VerificationNodeDataSetupResponse.VerificationLoadResponse;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.APMResponseParser;
import software.wings.service.impl.apm.APMSetupTestNodeData;
import software.wings.service.impl.appdynamics.AppdynamicsDataCollectionInfo;
import software.wings.service.impl.cloudwatch.CloudWatchDataCollectionInfo;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.datadog.DataDogSetupTestNodeData;
import software.wings.service.impl.dynatrace.DynaTraceDataCollectionInfo;
import software.wings.service.impl.dynatrace.DynaTraceTimeSeries;
import software.wings.service.impl.elk.ElkDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord.NewRelicMetricDataRecordKeys;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;
import software.wings.service.impl.prometheus.PrometheusDataCollectionInfo;
import software.wings.service.impl.splunk.SplunkDataCollectionInfo;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.impl.sumo.SumoDataCollectionInfo;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.CloudWatchService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FeatureFlagService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.verification.CV24x7DashboardService;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.verification.CVTaskService;
import software.wings.service.intfc.verification.DataCollectionInfoService;
import software.wings.settings.SettingValue;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.states.APMVerificationState.Method;
import software.wings.sm.states.AppDynamicsState;
import software.wings.sm.states.BugsnagState;
import software.wings.sm.states.DatadogLogState;
import software.wings.sm.states.DatadogState;
import software.wings.sm.states.DatadogState.Metric;
import software.wings.sm.states.DynatraceState;
import software.wings.sm.states.NewRelicState;
import software.wings.utils.Misc;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;
import software.wings.verification.CVTask;
import software.wings.verification.HeatMap;
import software.wings.verification.HeatMapResolution;
import software.wings.verification.TimeSeriesOfMetric;
import software.wings.verification.TransactionTimeSeries;
import software.wings.verification.VerificationDataAnalysisResponse;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.apm.APMCVServiceConfiguration;
import software.wings.verification.appdynamics.AppDynamicsCVServiceConfiguration;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;
import software.wings.verification.dashboard.HeatMapUnit;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration;
import software.wings.verification.log.BugsnagCVConfiguration;
import software.wings.verification.log.ElkCVConfiguration;
import software.wings.verification.log.LogsCVConfiguration;
import software.wings.verification.log.SplunkCVConfiguration;
import software.wings.verification.log.StackdriverCVConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@Singleton
@Slf4j
public class ContinuousVerificationServiceImpl implements ContinuousVerificationService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AuthService authService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AppService appService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private DataStoreService dataStoreService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject private SettingsService settingsService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private DelegateService delegateService;
  @Inject private CloudWatchService cloudWatchService;
  @Inject private CV24x7DashboardService cv24x7DashboardService;
  @Inject private DataCollectionExecutorService dataCollectionService;
  @Inject private AlertService alertService;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ContinuousVerificationService continuousVerificationService;
  @Inject private CVActivityLogService cvActivityLogService;
  @Inject private CVTaskService cvTaskService;
  @Inject private DataCollectionInfoService dataCollectionInfoService;

  private static final int PAGE_LIMIT = 999;
  private static final int START_OFFSET = 0;
  private static final String DATE_PATTERN = "yyyy-MM-dd HH:MM";
  public static final String HARNESS_DEFAULT_TAG = "_HARNESS_DEFAULT_TAG_";
  private static final String DUMMY_METRIC_NAME = "DummyMetricName";

  @Override
  public void saveCVExecutionMetaData(ContinuousVerificationExecutionMetaData continuousVerificationExecutionMetaData) {
    wingsPersistence.save(continuousVerificationExecutionMetaData);
  }

  @Override
  public void setMetaDataExecutionStatus(String stateExecutionId, ExecutionStatus status, boolean noData) {
    Query<ContinuousVerificationExecutionMetaData> query =
        wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class)
            .filter(ContinuousVerificationExecutionMetaDataKeys.stateExecutionId, stateExecutionId);

    wingsPersistence.update(query,
        wingsPersistence.createUpdateOperations(ContinuousVerificationExecutionMetaData.class)
            .set(ContinuousVerificationExecutionMetaDataKeys.executionStatus, status)
            .set(ContinuousVerificationExecutionMetaDataKeys.noData, noData));
  }

  @Override
  public LinkedHashMap<Long,
      LinkedHashMap<String,
          LinkedHashMap<String,
              LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
  getCVExecutionMetaData(String accountId, long beginEpochTs, long endEpochTs, final User user) throws ParseException {
    LinkedHashMap<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>> results =
        new LinkedHashMap<>();
    if (user == null) {
      // user is null, we can't validate permissions. Returning empty.
      logger.warn("Returning empty results from getCVExecutionMetaData since user was null");
      return results;
    }
    if (isEmpty(getAllowedApplicationsForUser(user, accountId))) {
      logger.info(
          "Returning empty results from getCVExecutionMetaData since user does not have permissions for any applications");
      return results;
    }
    PageRequest<ContinuousVerificationExecutionMetaData> request = PageRequestBuilder.aPageRequest()
                                                                       .addFilter("accountId", Operator.EQ, accountId)
                                                                       .addOrder("workflowStartTs", OrderType.DESC)
                                                                       .addOrder("stateStartTs", OrderType.ASC)
                                                                       .withLimit("500")
                                                                       .withOffset("0")
                                                                       .build();
    request.addFilter("workflowStartTs", Operator.GE, beginEpochTs);
    request.addFilter("workflowStartTs", Operator.LT, endEpochTs);
    request.addFilter("applicationId", Operator.IN, getAllowedApplicationsForUser(user, accountId).toArray());
    int previousOffset = 0;
    List<ContinuousVerificationExecutionMetaData> continuousVerificationExecutionMetaData = new ArrayList<>();
    PageResponse<ContinuousVerificationExecutionMetaData> response =
        wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, request);
    while (!response.isEmpty()) {
      continuousVerificationExecutionMetaData.addAll(response.getResponse());
      previousOffset += response.size();
      request.setOffset(String.valueOf(previousOffset));
      response = wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, request);
    }

    Map<String, Long> pipelineTimeStampMap = new HashMap<>();

    for (ContinuousVerificationExecutionMetaData executionMetaData : continuousVerificationExecutionMetaData) {
      if (executionMetaData.getPipelineExecutionId() != null) {
        if (!pipelineTimeStampMap.containsKey(executionMetaData.getPipelineExecutionId())) {
          pipelineTimeStampMap.put(executionMetaData.getPipelineExecutionId(), executionMetaData.getWorkflowStartTs());
        } else if (executionMetaData.getWorkflowStartTs()
            > pipelineTimeStampMap.get(executionMetaData.getPipelineExecutionId())) {
          pipelineTimeStampMap.put(executionMetaData.getPipelineExecutionId(), executionMetaData.getWorkflowStartTs());
        }
      }
    }

    Long startTimeTs;

    continuousVerificationExecutionMetaData =
        validatePermissionsAndGetAllowedExecutionList(user, accountId, continuousVerificationExecutionMetaData);
    for (ContinuousVerificationExecutionMetaData executionMetaData : continuousVerificationExecutionMetaData) {
      String pipeLineId = executionMetaData.getPipelineId();
      if (pipeLineId != null && pipelineTimeStampMap.containsKey(pipeLineId)) {
        startTimeTs = pipelineTimeStampMap.get(pipeLineId);
      } else {
        startTimeTs = executionMetaData.getWorkflowStartTs();
      }
      startTimeTs = Instant.ofEpochMilli(startTimeTs).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
      if (!results.containsKey(startTimeTs)) {
        results.put(startTimeTs, new LinkedHashMap<>());
      }

      String artifactName = executionMetaData.getArtifactName() == null
          ? "None-" + executionMetaData.getWorkflowExecutionId()
          : executionMetaData.getArtifactName();

      if (!results.get(startTimeTs).containsKey(artifactName)) {
        results.get(startTimeTs).put(artifactName, new LinkedHashMap<>());
      }

      String envWorkflowName = executionMetaData.getEnvName() + "/" + executionMetaData.getWorkflowName();
      if (!results.get(startTimeTs).get(artifactName).containsKey(envWorkflowName)) {
        results.get(startTimeTs).get(artifactName).put(envWorkflowName, new LinkedHashMap<>());
      }

      if (!results.get(startTimeTs)
               .get(artifactName)
               .get(envWorkflowName)
               .containsKey(executionMetaData.getWorkflowExecutionId())) {
        results.get(startTimeTs)
            .get(artifactName)
            .get(envWorkflowName)
            .put(executionMetaData.getWorkflowExecutionId(), new LinkedHashMap<>());
      }

      String phaseName = executionMetaData.getPhaseName() == null ? "BASIC" : executionMetaData.getPhaseName();

      if (!results.get(startTimeTs)
               .get(artifactName)
               .get(envWorkflowName)
               .get(executionMetaData.getWorkflowExecutionId())
               .containsKey(phaseName)) {
        results.get(startTimeTs)
            .get(artifactName)
            .get(envWorkflowName)
            .get(executionMetaData.getWorkflowExecutionId())
            .put(phaseName, new ArrayList<>());
      }
      results.get(startTimeTs)
          .get(artifactName)
          .get(envWorkflowName)
          .get(executionMetaData.getWorkflowExecutionId())
          .get(phaseName)
          .add(executionMetaData);
    }

    return results;
  }

  @Override
  public PageResponse<ContinuousVerificationExecutionMetaData> getAllCVExecutionsForTime(final String accountId,
      long beginEpochTs, long endEpochTs, boolean isTimeSeries,
      PageRequest<ContinuousVerificationExecutionMetaData> pageRequestFromUI) {
    // TODO: Move this accountId check to Rbac
    if (!featureFlagService.isEnabled(FeatureName.GLOBAL_CV_DASH, accountId)) {
      return new PageResponse<>();
    }
    PageRequest<ContinuousVerificationExecutionMetaData> pageRequest =
        PageRequestBuilder.aPageRequest().withOffset(pageRequestFromUI.getOffset()).build();
    if (beginEpochTs < 0 || endEpochTs < 0) {
      // if there's no start/end, we will default to 7 days
      beginEpochTs = Timestamp.currentMinuteBoundary() - TimeUnit.DAYS.toMillis(7);
      endEpochTs = Timestamp.currentMinuteBoundary();
    }
    List<StateType> stateTypeList;
    if (isTimeSeries) {
      stateTypeList = getMetricAnalysisStates();
    } else {
      stateTypeList = getLogAnalysisStates();
    }

    pageRequest.addFilter("stateType", Operator.IN, stateTypeList.toArray());
    pageRequest.addFilter("workflowStartTs", Operator.GE, beginEpochTs);
    pageRequest.addFilter("workflowStartTs", Operator.LT, endEpochTs);
    pageRequest.setFieldsIncluded(Arrays.asList("stateExecutionId", "workflowExecutionId", "envId", "serviceId",
        "accountId", "executionStatus", "applicationId", "workflowStartTs", "stateType"));
    pageRequest.addOrder("workflowStartTs", OrderType.DESC);
    pageRequest.addOrder("stateStartTs", OrderType.DESC);

    return wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, pageRequest, excludeAuthority);
  }

  @Override
  public List<ContinuousVerificationExecutionMetaData> getCVDeploymentData(
      PageRequest<ContinuousVerificationExecutionMetaData> pageRequest) {
    PageResponse<ContinuousVerificationExecutionMetaData> response =
        wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, pageRequest);
    List<ContinuousVerificationExecutionMetaData> continuousVerificationExecutionMetaData = new ArrayList<>();
    int previousOffset = 0;
    while (!response.isEmpty()) {
      continuousVerificationExecutionMetaData.addAll(response.getResponse());
      previousOffset += response.size();
      pageRequest.setOffset(String.valueOf(previousOffset));
      response = wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, pageRequest);
    }
    return continuousVerificationExecutionMetaData;
  }

  @Override
  public List<CVDeploymentData> getCVDeploymentData(
      String accountId, long startTime, long endTime, User user, String serviceId) {
    List<CVDeploymentData> results = new ArrayList<>();
    if (user == null) {
      // user is null, we can't validate permissions. Returning empty.
      logger.warn("Returning empty results from getCVDeploymentData since user was null");
      return results;
    }
    List<String> allowedApplications = getAllowedApplicationsForUser(user, accountId);
    if (isEmpty(allowedApplications)) {
      logger.info(
          "Returning empty results from getCVDeploymentData since user does not have permissions for any applications");
      return results;
    }

    PageRequest<ContinuousVerificationExecutionMetaData> request = PageRequestBuilder.aPageRequest()
                                                                       .addFilter("accountId", Operator.EQ, accountId)
                                                                       .addFilter("serviceId", Operator.EQ, serviceId)
                                                                       .addOrder("workflowStartTs", OrderType.DESC)
                                                                       .addOrder("stateStartTs", OrderType.ASC)
                                                                       .withLimit("500")
                                                                       .withOffset("0")
                                                                       .build();
    request.addFilter("workflowStartTs", Operator.GE, startTime);
    request.addFilter("workflowStartTs", Operator.LT, endTime);

    int previousOffset = 0;
    List<ContinuousVerificationExecutionMetaData> continuousVerificationExecutionMetaData = new ArrayList<>();
    PageResponse<ContinuousVerificationExecutionMetaData> response =
        wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, request);
    while (!response.isEmpty()) {
      continuousVerificationExecutionMetaData.addAll(response.getResponse());
      previousOffset += response.size();
      request.setOffset(String.valueOf(previousOffset));
      response = wingsPersistence.query(ContinuousVerificationExecutionMetaData.class, request);
    }

    Map<String, CVDeploymentData> deploymentData = new HashMap<>();
    for (ContinuousVerificationExecutionMetaData cvData : continuousVerificationExecutionMetaData) {
      if (!deploymentData.containsKey(cvData.getWorkflowExecutionId())) {
        deploymentData.put(cvData.getWorkflowExecutionId(), new CVDeploymentData(cvData));
      }
    }

    if (isEmpty(deploymentData)) {
      logger.info("There are no deployments with CV for service {}", serviceId);
      return new ArrayList<>();
    }
    // find the statuses of all the workflows we have.
    PageRequest<WorkflowExecution> workflowExecutionPageRequest =
        PageRequestBuilder.aPageRequest()
            .addFilter("_id", Operator.IN, deploymentData.keySet().toArray())
            .addFieldsIncluded("_id", "status", "startTs", "endTs", "name", "pipelineSummary")
            .withLimit("500")
            .withOffset("0")
            .build();
    previousOffset = 0;
    List<WorkflowExecution> workflowExecutionList = new ArrayList<>();
    PageResponse<WorkflowExecution> workflowExecutionResponse =
        wingsPersistence.query(WorkflowExecution.class, workflowExecutionPageRequest);
    while (!workflowExecutionResponse.isEmpty()) {
      workflowExecutionList.addAll(workflowExecutionResponse.getResponse());
      previousOffset += workflowExecutionResponse.size();
      workflowExecutionPageRequest.setOffset(String.valueOf(previousOffset));
      workflowExecutionResponse = wingsPersistence.query(WorkflowExecution.class, workflowExecutionPageRequest);
    }

    for (WorkflowExecution execution : workflowExecutionList) {
      deploymentData.get(execution.getUuid()).setStatus(execution.getStatus());
      deploymentData.get(execution.getUuid()).setWorkflowName(execution.normalizedName());
      PipelineSummary ps = execution.getPipelineSummary();
      if (ps != null) {
        deploymentData.get(execution.getUuid()).setPipelineName(ps.getPipelineName());
      }
    }
    return new ArrayList(deploymentData.values());
  }

  @Override
  public List<WorkflowExecution> getDeploymentsForService(
      String accountId, long startTime, long endTime, User user, String serviceId) {
    List<WorkflowExecution> results = new ArrayList<>();
    if (user == null) {
      // user is null, we can't validate permissions. Returning empty.
      logger.warn("Returning empty results from getCVDeploymentData since user was null");
      return results;
    }
    List<String> allowedApplications = getAllowedApplicationsForUser(user, accountId);
    Service service = wingsPersistence.get(Service.class, serviceId);
    if (isEmpty(allowedApplications) || service == null || !allowedApplications.contains(service.getAppId())) {
      logger.info(
          "Returning empty results from getCVDeploymentData since user {} does not have permissions for any applications",
          user);
      return results;
    }

    Set<String> fieldsNeeded = Sets.newHashSet(WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.endTs,
        WorkflowExecutionKeys.name, WorkflowExecutionKeys.pipelineExecutionId, WorkflowExecutionKeys.appId,
        WorkflowExecutionKeys.serviceIds, WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.status,
        WorkflowExecutionKeys.envId, WorkflowExecutionKeys.uuid, WorkflowExecutionKeys.workflowId);

    try (HIterator<WorkflowExecution> records =
             workflowExecutionService.executions(service.getAppId(), startTime, endTime, fieldsNeeded)) {
      while (records.hasNext()) {
        results.add(records.next());
      }
    }

    List<WorkflowExecution> resultList = new ArrayList<>();
    results.forEach(workflowExecution -> {
      if (workflowExecution.getServiceIds() != null && workflowExecution.getServiceIds().contains(serviceId)) {
        resultList.add(workflowExecution);
      }
    });
    return resultList;
  }

  /**
   * Check if the user has permissions to view the executionData.
   *
   * @param user
   * @param accountId
   * @param executionMetaDataList
   * @return true if user has all the required permissions, false otherwise.
   */
  private List<ContinuousVerificationExecutionMetaData> validatePermissionsAndGetAllowedExecutionList(final User user,
      final String accountId, final List<ContinuousVerificationExecutionMetaData> executionMetaDataList) {
    List<ContinuousVerificationExecutionMetaData> finalList = new ArrayList<>();
    Map<String, AppPermissionSummary> userAppPermissions =
        authService.getUserPermissionInfo(accountId, user, false).getAppPermissionMapInternal();
    //"Cache" it by applicationId.
    Map<String, Set<String>> servicePermissionsByApp = new HashMap<>();
    Map<String, Set<String>> envPermissionsByApp = new HashMap<>();

    for (ContinuousVerificationExecutionMetaData executionMetaData : executionMetaDataList) {
      final String applicationId = executionMetaData.getApplicationId();
      Set<String> servicePermissions, pipelinePermissions, wfPermissions, envPermissions;

      if (!servicePermissionsByApp.containsKey(applicationId)) {
        // If it's  not present for servicePermissions,it's not present for anything. So fill up the map.
        servicePermissionsByApp.put(
            applicationId, userAppPermissions.get(applicationId).getServicePermissions().get(Action.READ));
        envPermissionsByApp.put(applicationId, getEnvPermissions(userAppPermissions, applicationId));
      }
      servicePermissions = servicePermissionsByApp.get(applicationId);
      envPermissions = envPermissionsByApp.get(applicationId);
      logger.info("Service permissions for user {} are {}", user.getName(), servicePermissions);
      logger.info("environment permissions for user {} are {}", user.getName(), envPermissions);

      if (checkIfPermissionsApproved(servicePermissions, executionMetaData.getServiceId())
          && checkIfPermissionsApproved(envPermissions, executionMetaData.getEnvId())) {
        finalList.add(executionMetaData);
      } else {
        logger.info("User {} does not have permissions to view the execution data {} and {} and {} and {}",
            user.getName(), executionMetaData.getServiceName(), executionMetaData.getWorkflowName(),
            executionMetaData.getEnvName(), executionMetaData.getPipelineName());
        logger.info("User {} does not have permissions to view the execution data {} and {} and {} and {}",
            user.getName(), executionMetaData.getServiceId(), executionMetaData.getWorkflowId(),
            executionMetaData.getEnvId(), executionMetaData.getPipelineId());
      }
    }
    return finalList;
  }

  private Set<String> getEnvPermissions(Map<String, AppPermissionSummary> userAppPermissions, String applicationId) {
    if (isEmpty(userAppPermissions)) {
      return emptySet();
    }

    AppPermissionSummary appPermissionSummary = userAppPermissions.get(applicationId);

    if (appPermissionSummary == null) {
      return emptySet();
    }

    Map<Action, Set<EnvInfo>> envPermissions = appPermissionSummary.getEnvPermissions();

    if (isEmpty(envPermissions)) {
      return emptySet();
    }

    Set<EnvInfo> envInfoSet = envPermissions.get(Action.READ);
    if (isEmpty(envInfoSet)) {
      return emptySet();
    }

    return envInfoSet.stream().map(envInfo -> envInfo.getEnvId()).collect(Collectors.toSet());
  }

  /**
   * @param setToCheck
   * @param value
   * @return False if set is either empty or it does not contain value. True otherwise.
   */
  private boolean checkIfPermissionsApproved(final Set<String> setToCheck, final String value) {
    if (isEmpty(value)) {
      return true;
    }

    if (isEmpty(setToCheck) || !setToCheck.contains(value)) {
      logger.info("Permissions rejected for value {} in set {}", value, setToCheck);
      return false;
    }
    return true;
  }

  private List<String> getAllowedApplicationsForUser(final User user, final String accountId) {
    Map<String, AppPermissionSummary> userApps =
        authService.getUserPermissionInfo(accountId, user, false).getAppPermissionMapInternal();
    return new ArrayList<>(userApps.keySet());
  }

  @Override
  public List<HeatMap> getHeatMap(
      String accountId, String appId, String serviceId, long startTime, long endTime, boolean detailed) {
    List<HeatMap> rv = Collections.synchronizedList(new ArrayList<>());

    List<CVConfiguration> cvConfigurations = wingsPersistence.createQuery(CVConfiguration.class)
                                                 .filter("appId", appId)
                                                 .filter(CVConfigurationKeys.serviceId, serviceId)
                                                 .filter(CVConfigurationKeys.enabled24x7, true)
                                                 .asList();

    if (isEmpty(cvConfigurations)) {
      logger.info("No cv config found for appId={}, serviceId={}", appId, serviceId);
      return new ArrayList<>();
    }

    List<Callable<Void>> callables = new ArrayList<>();
    cvConfigurations.stream()
        .filter(cvConfig -> !getLogAnalysisStates().contains(cvConfig.getStateType()))
        .forEach(cvConfig -> callables.add(() -> {
          cvConfigurationService.fillInServiceAndConnectorNames(cvConfig);
          String envName = cvConfig.getEnvName();
          logger.info("Environment name = " + envName);
          final HeatMap heatMap = HeatMap.builder().cvConfiguration(cvConfig).build();
          rv.add(heatMap);

          List<HeatMapUnit> units = createAllHeatMapUnits(startTime, endTime, cvConfig);
          List<HeatMapUnit> resolvedUnits = resolveHeatMapUnits(units, startTime, endTime);
          heatMap.getRiskLevelSummary().addAll(resolvedUnits);
          return null;
        }));
    dataCollectionService.executeParrallel(callables);

    rv.addAll(cv24x7DashboardService.getHeatMapForLogs(accountId, appId, serviceId, startTime, endTime, detailed));

    return rv;
  }

  /**
   *
   * @param units - List of heat map units with the smallest possible size (currently = 1 cron job interval)
   * @param startTime - in ms
   * @param endTime - in ms
   * @return - List of heatmap units based on resolution determined by startTime, endTime
   */
  private List<HeatMapUnit> resolveHeatMapUnits(List<HeatMapUnit> units, long startTime, long endTime) {
    List<HeatMapUnit> resolvedUnits = new ArrayList<>();
    HeatMapResolution heatMapResolution = HeatMapResolution.getResolution(startTime, endTime);

    // time duration represented by each read unit
    int unitDuration = heatMapResolution.getDurationOfHeatMapUnit(heatMapResolution);

    // number of small units to be merged into one reqd unit
    int eventsPerUnit = heatMapResolution.getEventsPerHeatMapUnit(heatMapResolution);

    // total number of read units
    int numberOfUnits = (int) ceil((double) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime) / unitDuration);

    logger.info("total small units = {}, number of required units = {}", units.size(), numberOfUnits);

    for (int i = 0; i < numberOfUnits; i++) {
      // merge [i * eventsPerUnit, (i + 1) * eventsPerUnit)
      // [x, y) denotes x inclusive, y exclusive
      // Note: This works because the smallest unit is composed of exactly 1 event
      int startIndex = i * eventsPerUnit;
      int endIndex = min((i + 1) * eventsPerUnit, units.size());

      if (startIndex >= endIndex) {
        continue;
      }
      List<HeatMapUnit> subList = units.subList(startIndex, endIndex);
      if (subList.size() > 0) {
        resolvedUnits.add(merge(subList));
      }
    }
    return resolvedUnits;
  }

  private HeatMapUnit merge(List<HeatMapUnit> units) {
    HeatMapUnit mergedUnit = HeatMapUnit.builder()
                                 .startTime(units.get(0).getStartTime())
                                 .endTime(units.get(units.size() - 1).getEndTime())
                                 .overallScore(-2)
                                 .build();
    units.forEach(unit -> {
      if (unit.getScoreList() != null) {
        mergedUnit.updateOverallScore(unit.getOverallScore());
      }
    });

    return mergedUnit;
  }

  private List<TimeSeriesMLAnalysisRecord> mergeRecordsOfDifferentTagsIfNecessary(
      List<TimeSeriesMLAnalysisRecord> records) {
    List<TimeSeriesMLAnalysisRecord> retList = new ArrayList<>();
    Map<String, List<TimeSeriesMLAnalysisRecord>> minuteConfigMap = new LinkedHashMap<>();
    if (isNotEmpty(records)) {
      records.forEach(record -> {
        String key = record.getCvConfigId() + "::" + record.getAnalysisMinute();
        if (!minuteConfigMap.containsKey(key)) {
          minuteConfigMap.put(key, new ArrayList<>());
        }
        minuteConfigMap.get(key).add(record);
      });

      minuteConfigMap.forEach((key, recordList) -> {
        // aggregate the records/overallscores.
        if (recordList.size() == 1) {
          retList.add(recordList.get(0));
        } else {
          List<Double> scoreList = new ArrayList<>();
          recordList.forEach(record -> {
            if (isNotEmpty(record.getOverallMetricScores())) {
              scoreList.add(Collections.max(record.getOverallMetricScores().values()));
            }
          });
          TimeSeriesMLAnalysisRecord aggregatedRecord = minuteConfigMap.get(key).get(0);
          Double aggregatedScore = -1.0;
          if (scoreList.size() > 0) {
            aggregatedScore = scoreList.stream().mapToDouble(val -> val).average().orElse(0.0);
          }

          Map<String, Double> aggrOverallScores = new HashMap<>();
          aggrOverallScores.put(DUMMY_METRIC_NAME, aggregatedScore);
          aggregatedRecord.setOverallMetricScores(aggrOverallScores);
          retList.add(aggregatedRecord);
        }
      });
    }
    return retList;
  }

  private List<HeatMapUnit> createAllHeatMapUnits(long startTime, long endTime, CVConfiguration cvConfiguration) {
    long cronPollIntervalMs = TimeUnit.MINUTES.toMillis(CRON_POLL_INTERVAL_IN_MINUTES);
    Preconditions.checkState((endTime - startTime) >= cronPollIntervalMs);
    List<TimeSeriesMLAnalysisRecord> records = getAnalysisRecordsInTimeRange(startTime, endTime, cvConfiguration);
    records = mergeRecordsOfDifferentTagsIfNecessary(records);
    return computeHeatMapUnits(startTime, endTime, records, null);
  }

  public static List<HeatMapUnit> computeHeatMapUnits(long startTime, long endTime,
      List<TimeSeriesMLAnalysisRecord> timeSeriesAnalysisRecords, List<LogMLAnalysisRecord> logAnalysisRecords) {
    Preconditions.checkState(timeSeriesAnalysisRecords == null || logAnalysisRecords == null,
        "both time series and log analysis records can not be present");

    long startMinute = TimeUnit.MILLISECONDS.toMinutes(startTime);
    long endMinute = TimeUnit.MILLISECONDS.toMinutes(endTime);

    List<HeatMapUnit> units = new ArrayList<>();
    if (isEmpty(timeSeriesAnalysisRecords) && isEmpty(logAnalysisRecords)) {
      while (endMinute > startMinute) {
        units.add(HeatMapUnit.builder()
                      .startTime(TimeUnit.MINUTES.toMillis(startMinute))
                      .endTime(TimeUnit.MINUTES.toMillis(startMinute + CRON_POLL_INTERVAL_IN_MINUTES))
                      .na(1)
                      .overallScore(-2)
                      .build());
        startMinute += CRON_POLL_INTERVAL_IN_MINUTES;
      }

      return units;
    }

    SortedSet<HeatMapUnit> sortedUnitsFromDB = new TreeSet<>();

    if (isNotEmpty(timeSeriesAnalysisRecords)) {
      timeSeriesAnalysisRecords.forEach(record -> {
        HeatMapUnit heatMapUnit =
            HeatMapUnit.builder()
                .startTime(TimeUnit.MINUTES.toMillis(record.getAnalysisMinute() - CRON_POLL_INTERVAL_IN_MINUTES) + 1)
                .endTime(TimeUnit.MINUTES.toMillis(record.getAnalysisMinute()))
                .overallScore(-2)
                .build();

        heatMapUnit.updateOverallScore(record.getOverallMetricScores());
        sortedUnitsFromDB.add(heatMapUnit);
      });
    }

    if (isNotEmpty(logAnalysisRecords)) {
      logAnalysisRecords.forEach(record -> {
        HeatMapUnit heatMapUnit =
            HeatMapUnit.builder()
                .startTime(
                    TimeUnit.MINUTES.toMillis(record.getLogCollectionMinute() - CRON_POLL_INTERVAL_IN_MINUTES) + 1)
                .endTime(TimeUnit.MINUTES.toMillis(record.getLogCollectionMinute()))
                .overallScore(-2)
                .build();

        heatMapUnit.updateOverallScore(record.getScore());
        sortedUnitsFromDB.add(heatMapUnit);
      });
    }

    long cronPollIntervalMs = TimeUnit.MINUTES.toMillis(CRON_POLL_INTERVAL_IN_MINUTES);

    // find the actual start time so that we fill from there
    HeatMapUnit heatMapUnit = sortedUnitsFromDB.first();
    long actualUnitStartTime = heatMapUnit.getStartTime();
    while (startTime < actualUnitStartTime - cronPollIntervalMs) {
      actualUnitStartTime -= cronPollIntervalMs;
    }

    int dbUnitIndex = 0;
    List<HeatMapUnit> unitsFromDB = new ArrayList<>(sortedUnitsFromDB);
    for (long unitTime = actualUnitStartTime; unitTime <= endTime; unitTime += cronPollIntervalMs) {
      heatMapUnit = dbUnitIndex < sortedUnitsFromDB.size() ? unitsFromDB.get(dbUnitIndex) : null;
      if (heatMapUnit != null) {
        long timeDifference = TimeUnit.MILLISECONDS.toSeconds(abs(heatMapUnit.getStartTime() - unitTime));
        if (timeDifference != 0 && timeDifference < 60) {
          logger.error(
              "Unexpected state: timeDifference = {}, should have been 0 or > 60, heatmap unit start time = {}",
              timeDifference, heatMapUnit.getStartTime());
        }
      }

      if (heatMapUnit != null && unitTime == heatMapUnit.getStartTime()) {
        units.add(heatMapUnit);
        dbUnitIndex++;
        continue;
      }

      units.add(HeatMapUnit.builder()
                    .endTime(unitTime + cronPollIntervalMs - 1)
                    .startTime(unitTime)
                    .overallScore(-2)
                    .na(1)
                    .build());
    }
    return units;
  }

  @NotNull
  private List<TimeSeriesMLAnalysisRecord> getAnalysisRecordsInTimeRange(
      long startTime, long endTime, CVConfiguration cvConfiguration) {
    List<TimeSeriesMLAnalysisRecord> timeSeriesMLAnalysisRecords = new ArrayList<>();
    try (HIterator<TimeSeriesMLAnalysisRecord> analysisRecords = new HIterator<>(
             wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority)
                 .filter(MetricAnalysisRecordKeys.cvConfigId, cvConfiguration.getUuid())
                 .field(MetricAnalysisRecordKeys.analysisMinute)
                 .greaterThanOrEq(TimeUnit.MILLISECONDS.toMinutes(startTime) + CRON_POLL_INTERVAL_IN_MINUTES)
                 .field(MetricAnalysisRecordKeys.analysisMinute)
                 .lessThanOrEq(TimeUnit.MILLISECONDS.toMinutes(endTime))
                 .order(MetricAnalysisRecordKeys.analysisMinute)
                 .project(MetricAnalysisRecordKeys.transactions, false)
                 .project(MetricAnalysisRecordKeys.transactionsCompressedJson, false)
                 .fetch())) {
      while (analysisRecords.hasNext()) {
        timeSeriesMLAnalysisRecords.add(analysisRecords.next());
      }
    }
    timeSeriesMLAnalysisRecords.forEach(
        timeSeriesMLAnalysisRecord -> timeSeriesMLAnalysisRecord.decompressTransactions());

    return timeSeriesMLAnalysisRecords;
  }

  @NotNull
  public Map<String, Map<String, TimeSeriesOfMetric>> fetchObservedTimeSeries(
      long startTime, long endTime, CVConfiguration cvConfiguration, long historyStartTime) {
    // 1. Get time series for the entire duration from historyStartTime to endTime
    // 2. Pass startTime as the riskCutOffTime as that's the starting point where we consider risk
    if (TimeUnit.MILLISECONDS.toDays(endTime - historyStartTime) > 30L) {
      historyStartTime = startTime - TimeUnit.HOURS.toMillis(2) + 1;
    }
    return getTimeSeriesForTimeRangeFromDataRecords(cvConfiguration,
        TimeSeriesFilter.builder().startTime(startTime).endTime(endTime).historyStartTime(historyStartTime).build())
        .get(HARNESS_DEFAULT_TAG);
  }

  private SettingValue getConnectorConfig(CVConfiguration cvConfiguration) {
    if (isNotEmpty(cvConfiguration.getConnectorId())) {
      return wingsPersistence.get(SettingAttribute.class, cvConfiguration.getConnectorId()).getValue();
    }
    return null;
  }

  private Double getNormalizedMetricValue(String metricName, NewRelicMetricDataRecord dataRecord, StateType stateType) {
    switch (stateType) {
      case APP_DYNAMICS:
        return AppDynamicsState.getNormalizedValue(metricName, dataRecord);
      case NEW_RELIC:
        return NewRelicState.getNormalizedErrorMetric(metricName, dataRecord);
      default:
        return dataRecord.getValues().get(metricName);
    }
  }

  private String getDisplayNameOfMetric(String metricName) {
    if (ERROR_METRIC_NAMES.containsKey(metricName)) {
      return ERROR_METRIC_NAMES.get(metricName);
    }
    return metricName;
  }

  @VisibleForTesting
  String getMetricType(CVConfiguration cvConfig, String metricName) {
    switch (cvConfig.getStateType()) {
      case APP_DYNAMICS:
        return AppDynamicsState.getMetricTypeForMetric(metricName);
      case NEW_RELIC:
        return NewRelicState.getMetricTypeForMetric(metricName);
      case PROMETHEUS:
        PrometheusCVServiceConfiguration prometheusCVServiceConfiguration = (PrometheusCVServiceConfiguration) cvConfig;
        return prometheusCVServiceConfiguration.getTimeSeriesToAnalyze()
            .stream()
            .filter(timeSeries -> timeSeries.getMetricName().equals(metricName))
            .findAny()
            .map(TimeSeries::getMetricType)
            .orElse(null);
      case APM_VERIFICATION:
        APMCVServiceConfiguration apmcvServiceConfiguration = (APMCVServiceConfiguration) cvConfig;
        return apmcvServiceConfiguration.getMetricCollectionInfos()
            .stream()
            .filter(metricCollectionInfo -> metricCollectionInfo.getMetricName().equals(metricName))
            .findAny()
            .map(x -> x.getMetricType().name())
            .orElse(null);
      case DATA_DOG:
        return DatadogState.getMetricTypeForMetric(metricName, (DatadogCVServiceConfiguration) cvConfig);
      case DYNA_TRACE:
        return DynatraceState.getMetricTypeForMetric(metricName);
      case CLOUD_WATCH:
        CloudWatchCVServiceConfiguration cloudWatchCVServiceConfiguration = (CloudWatchCVServiceConfiguration) cvConfig;
        List<CloudWatchMetric> metrics = new ArrayList<>();
        if (isNotEmpty(cloudWatchCVServiceConfiguration.getEc2Metrics())) {
          metrics.addAll(cloudWatchCVServiceConfiguration.getEc2Metrics());
        }

        if (isNotEmpty(cloudWatchCVServiceConfiguration.getLambdaFunctionsMetrics())) {
          metrics.addAll(cloudWatchCVServiceConfiguration.getLambdaFunctionsMetrics()
                             .values()
                             .stream()
                             .flatMap(List::stream)
                             .collect(Collectors.toList()));
        }
        if (isNotEmpty(cloudWatchCVServiceConfiguration.getEcsMetrics())) {
          metrics.addAll(cloudWatchCVServiceConfiguration.getEcsMetrics()
                             .values()
                             .stream()
                             .flatMap(List::stream)
                             .collect(Collectors.toList()));
        }
        if (isNotEmpty(cloudWatchCVServiceConfiguration.getLoadBalancerMetrics())) {
          metrics.addAll(cloudWatchCVServiceConfiguration.getLoadBalancerMetrics()
                             .values()
                             .stream()
                             .flatMap(List::stream)
                             .collect(Collectors.toList()));
        }
        return metrics.stream()
            .filter(metric -> metric.getMetricName().equals(metricName))
            .findAny()
            .map(CloudWatchMetric::getMetricType)
            .orElse(null);
      default:
        logger.info("Unsupported stateType {} for deeplinking", cvConfig.getStateType());
        return null;
    }
  }

  private void setDeeplinkUrlInRecords(CVConfiguration cvConfiguration, SettingValue connectorConfig, long startTime,
      long endTime, List<NewRelicMetricDataRecord> records) {
    if (isNotEmpty(records)) {
      records.forEach(record -> {
        if (isNotEmpty(record.getDeeplinkMetadata())) {
          record.getDeeplinkMetadata().forEach((metric, metadata) -> {
            if (record.getDeeplinkUrl() == null) {
              record.setDeeplinkUrl(new HashMap<>());
            }
            String link = getDeeplinkUrl(cvConfiguration, connectorConfig, startTime, endTime, metadata);
            record.getDeeplinkUrl().put(metric, link);
          });
        }
      });
    }
  }

  private String getDeeplinkUrl(
      CVConfiguration cvConfig, SettingValue connectorConfig, long startTime, long endTime, String metricString) {
    switch (cvConfig.getStateType()) {
      case APP_DYNAMICS:
        AppDynamicsCVServiceConfiguration config = (AppDynamicsCVServiceConfiguration) cvConfig;
        String baseUrl = ((AppDynamicsConfig) connectorConfig).getControllerUrl();
        return baseUrl
            + APPDYNAMICS_DEEPLINK_FORMAT.replace("{startTimeMs}", String.valueOf(startTime))
                  .replace("{endTimeMs}", String.valueOf(endTime))
                  .replace("{applicationId}", config.getAppDynamicsApplicationId())
                  .replace("{metricString}", metricString);
      case NEW_RELIC:
        String newRelicAppId = ((NewRelicCVServiceConfiguration) cvConfig).getApplicationId();
        String newRelicAccountId = ((NewRelicConfig) connectorConfig).getNewRelicAccountId();
        if (isEmpty(newRelicAccountId)) {
          return "";
        }
        int durationInHours = (int) TimeUnit.MILLISECONDS.toHours(endTime - startTime);
        long endTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(endTime);
        return NEW_RELIC_DEEPLINK_FORMAT.replace("{accountId}", newRelicAccountId)
            .replace("{applicationId}", newRelicAppId)
            .replace("{duration}", String.valueOf(durationInHours))
            .replace("{endTime}", String.valueOf(endTimeSeconds));
      case PROMETHEUS:
        int durationInMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
        SimpleDateFormat format = new SimpleDateFormat(DATE_PATTERN);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String endDate = format.format(new Date(endTime));
        try {
          endDate = URLEncoder.encode(endDate, "UTF-8");
        } catch (UnsupportedEncodingException e) {
          logger.error("Unable to encode the time range : ", durationInMinutes);
          throw new WingsException(e);
        }
        String url = ((PrometheusConfig) connectorConfig).getUrl();
        return PROMETHEUS_DEEPLINK_FORMAT.replace("{baseUrl}", url)
            .replace("{rangeInput}", String.valueOf(durationInMinutes))
            .replace("{endTime}", endDate)
            .replace("{metricString}", String.valueOf(metricString));
      default:
        logger.info("Unsupported stateType {} for deeplinking", cvConfig.getStateType());
        return "";
    }
  }

  private void updateRisksFromSummary(long startTime, long endTime, long riskCutOff, CVConfiguration cvConfiguration,
      Map<String, Map<String, Map<String, TimeSeriesOfMetric>>> observedTimeSeriesWithTag) {
    observedTimeSeriesWithTag.forEach((tag, observedTimeSeries) -> {
      Query<TimeSeriesRiskSummary> timeSeriesRiskSummaryQuery =
          wingsPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority)
              .filter(TimeSeriesRiskSummaryKeys.cvConfigId, cvConfiguration.getUuid())
              .field(TimeSeriesRiskSummaryKeys.analysisMinute)
              .greaterThan(TimeUnit.MILLISECONDS.toMinutes(startTime))
              .field(TimeSeriesRiskSummaryKeys.analysisMinute)
              .lessThanOrEq(TimeUnit.MILLISECONDS.toMinutes(endTime))
              .order(TimeSeriesRiskSummaryKeys.analysisMinute);
      if (!tag.equals(HARNESS_DEFAULT_TAG)) {
        timeSeriesRiskSummaryQuery.filter(TimeSeriesRiskSummaryKeys.tag, tag);
      }
      List<TimeSeriesRiskSummary> riskSummaries = timeSeriesRiskSummaryQuery.asList();

      riskSummaries.forEach(summary -> summary.decompressMaps());

      for (TimeSeriesRiskSummary summary : riskSummaries) {
        observedTimeSeries.forEach((transaction, metricMap) -> {
          // TODO: Remove these two loops.
          metricMap.entrySet()
              .stream()
              .filter(e
                  -> isNotEmpty(summary.getTxnMetricRisk()) && summary.getTxnMetricRisk().containsKey(transaction)
                      && summary.getTxnMetricRisk().get(transaction).containsKey(e.getKey()))
              .forEach(entry -> {
                Integer risk = summary.getTxnMetricRisk().get(transaction).get(entry.getKey());
                if (TimeUnit.MINUTES.toMillis(summary.getAnalysisMinute()) > riskCutOff) {
                  observedTimeSeries.get(transaction).get(entry.getKey()).updateRisk(risk);
                }
                observedTimeSeries.get(transaction)
                    .get(entry.getKey())
                    .addToRiskMap(TimeUnit.MINUTES.toMillis(summary.getAnalysisMinute()), risk);
              });

          metricMap.entrySet()
              .stream()
              .filter(e
                  -> isNotEmpty(summary.getTxnMetricLongTermPattern())
                      && summary.getTxnMetricLongTermPattern().containsKey(transaction)
                      && summary.getTxnMetricLongTermPattern().get(transaction).containsKey(e.getKey()))
              .forEach(entry -> {
                Integer pattern = summary.getTxnMetricLongTermPattern().get(transaction).get(entry.getKey());
                observedTimeSeries.get(transaction).get(entry.getKey()).setLongTermPattern(pattern);
              });

          // TODO: Keep just this loop going forward and move risk and longtermPattern to this.
          metricMap.entrySet()
              .stream()
              .filter(e
                  -> isNotEmpty(summary.getTxnMetricRiskData())
                      && summary.getTxnMetricRiskData().containsKey(transaction)
                      && summary.getTxnMetricRiskData().get(transaction).containsKey(e.getKey()))
              .forEach(entry -> {
                Integer pattern =
                    summary.getTxnMetricRiskData().get(transaction).get(entry.getKey()).getLongTermPattern();
                long lastSeenTime =
                    summary.getTxnMetricRiskData().get(transaction).get(entry.getKey()).getLastSeenTime();
                Integer risk = summary.getTxnMetricRisk().get(transaction).get(entry.getKey());
                observedTimeSeries.get(transaction).get(entry.getKey()).setLastSeenTime(lastSeenTime);
              });
        });
      }
    });
  }

  @Override
  public SortedSet<TransactionTimeSeries> getTimeSeriesOfHeatMapUnit(TimeSeriesFilter filter) {
    CVConfiguration cvConfiguration = wingsPersistence.get(CVConfiguration.class, filter.getCvConfigId());
    if (cvConfiguration == null) {
      logger.info("No cvConfig found for cvConfigId={}", filter.getCvConfigId());
      return new TreeSet<>();
    }

    populateMetricNames(cvConfiguration, filter);
    filter.setStartTime(Timestamp.nextMinuteBoundary(filter.getStartTime()));
    filter.setEndTime(Timestamp.minuteBoundary(filter.getEndTime()));
    filter.setHistoryStartTime(Timestamp.nextMinuteBoundary(filter.getHistoryStartTime()));

    // 1. Get time series for the entire duration from historyStartTime to endTime
    // 2. Pass startTime as the riskCutOffTime as that's the starting point where we consider risk
    if (TimeUnit.MILLISECONDS.toDays(filter.getEndTime() - filter.getHistoryStartTime()) > 30L) {
      filter.setHistoryStartTime(filter.getStartTime() - TimeUnit.HOURS.toMillis(2) + 1);
    }
    return convertTimeSeriesResponse(getTimeSeriesForTimeRangeFromDataRecords(cvConfiguration, filter));
  }

  private void populateMetricNames(CVConfiguration cvConfiguration, TimeSeriesFilter filter) {
    if (isEmpty(filter.getMetricNames())) {
      return;
    }
    switch (cvConfiguration.getStateType()) {
      case APP_DYNAMICS:
        if (filter.getMetricNames().contains(AppdynamicsConstants.ERROR_DISPLAY_METRIC_NAME)) {
          filter.getMetricNames().remove(AppdynamicsConstants.ERROR_DISPLAY_METRIC_NAME);
          filter.getMetricNames().add(AppdynamicsConstants.ERRORS_PER_MINUTE);
        }

        if (filter.getMetricNames().contains(AppdynamicsConstants.STALL_COUNT_DISPLAY_METRIC_NAME)) {
          filter.getMetricNames().remove(AppdynamicsConstants.STALL_COUNT_DISPLAY_METRIC_NAME);
          filter.getMetricNames().add(AppdynamicsConstants.STALL_COUNT);
        }
        break;

      case NEW_RELIC:
        if (filter.getMetricNames().contains(NewRelicMetricValueDefinition.ERROR_DISPLAY_METRIC_NAME)) {
          filter.getMetricNames().remove(NewRelicMetricValueDefinition.ERROR_DISPLAY_METRIC_NAME);
          filter.getMetricNames().add(NewRelicMetricValueDefinition.ERROR);
        }
        break;

      default:
        throw new WingsException("Invalid State: " + cvConfiguration.getStateType());
    }
  }

  private SortedSet<TransactionTimeSeries> convertTimeSeriesResponse(
      Map<String, Map<String, Map<String, TimeSeriesOfMetric>>> observedTimeSeriesTags) {
    SortedSet<TransactionTimeSeries> resp = new TreeSet<>();
    for (Map.Entry<String, Map<String, Map<String, TimeSeriesOfMetric>>> observedTimeSeries :
        observedTimeSeriesTags.entrySet()) {
      String tag = observedTimeSeries.getKey();
      for (Map.Entry<String, Map<String, TimeSeriesOfMetric>> txnEntry : observedTimeSeries.getValue().entrySet()) {
        TransactionTimeSeries txnTimeSeries = new TransactionTimeSeries();
        txnTimeSeries.setTag(tag);
        txnTimeSeries.setTransactionName(txnEntry.getKey());
        txnTimeSeries.setMetricTimeSeries(new TreeSet<>());
        for (Map.Entry<String, TimeSeriesOfMetric> metricEntry : txnEntry.getValue().entrySet()) {
          txnTimeSeries.getMetricTimeSeries().add(metricEntry.getValue());
        }
        resp.add(txnTimeSeries);
      }
    }

    if (resp.size() > 0) {
      String tagWithMaxRisk = resp.first().getTag();
      resp.removeIf(timeseries -> !timeseries.getTag().equals(tagWithMaxRisk));
    }
    // logger.info("Timeseries response = {}", resp);
    logger.info("TimeSeries response size is : {}", resp.size());
    return resp;
  }

  private Map<String, Map<String, Map<String, TimeSeriesOfMetric>>> getTimeSeriesForTimeRangeFromDataRecords(
      CVConfiguration cvConfiguration, TimeSeriesFilter filter) {
    // The object to be returned which contains the map tag => txn => metrics => timeseries per metric
    Map<String, Map<String, Map<String, TimeSeriesOfMetric>>> observedTimeSeries = new ConcurrentHashMap<>();

    long startTime = filter.getHistoryStartTime();
    long endTime = filter.getEndTime();
    long riskCutOffTime = filter.getStartTime();

    SettingValue connectorConfig = getConnectorConfig(cvConfiguration);
    int endMinute = (int) TimeUnit.MILLISECONDS.toMinutes(endTime);
    int startMinute = (int) TimeUnit.MILLISECONDS.toMinutes(startTime);

    final Set<NewRelicMetricDataRecord> metricRecords = ConcurrentHashMap.newKeySet();
    Map<Integer, Integer> startEndMap = new HashMap<>();
    int movingStart = startMinute, movingEnd = startMinute + 60;
    while (movingEnd <= endMinute) {
      startEndMap.put(movingStart, movingEnd);
      movingStart = movingEnd;
      movingEnd = movingStart + 60;
    }
    if (movingEnd > endMinute) {
      startEndMap.put(movingStart, endMinute);
    }

    List<Callable<Void>> callables = new ArrayList<>();
    startEndMap.forEach((start, end) -> callables.add(() -> {
      final List<NewRelicMetricDataRecord> records = new ArrayList<>();
      PageRequest<TimeSeriesDataRecord> dataRecordPageRequest =
          PageRequestBuilder.aPageRequest()
              .addFilter(TimeSeriesMetricRecordKeys.cvConfigId, Operator.EQ, cvConfiguration.getUuid())
              .addFilter(TimeSeriesMetricRecordKeys.dataCollectionMinute, Operator.GE, start)
              .build();

      if (end == endMinute) {
        dataRecordPageRequest.addFilter(TimeSeriesMetricRecordKeys.dataCollectionMinute, Operator.LT_EQ, end);
      } else {
        dataRecordPageRequest.addFilter(TimeSeriesMetricRecordKeys.dataCollectionMinute, Operator.LT, end);
      }

      if (isNotEmpty(filter.getTags()) && filter.getTags().size() == 1) {
        dataRecordPageRequest.addFilter(
            NewRelicMetricDataRecordKeys.tag, Operator.EQ, filter.getTags().iterator().next());
      }

      dataRecordPageRequest.setLimit(UNLIMITED);
      PageResponse<TimeSeriesDataRecord> dataRecords =
          dataStoreService.list(TimeSeriesDataRecord.class, dataRecordPageRequest);
      TimeSeriesDataRecord.getNewRelicDataRecordsFromTimeSeriesDataRecords(dataRecords.getResponse())
          .stream()
          .filter(dataRecord -> !HEARTBEAT_METRIC_NAME.equals(dataRecord.getName()))
          .forEach(dataRecord -> {
            // filter for txnName
            if (isEmpty(filter.getTxnNames())) {
              records.add(dataRecord);
            } else if (filter.getTxnNames().contains(dataRecord.getName())) {
              records.add(dataRecord);
            }
          });
      filterMetrics(filter, records);
      setDeeplinkUrlInRecords(cvConfiguration, connectorConfig, startTime, endTime, records);
      metricRecords.addAll(records);
      return null;
    }));

    dataCollectionService.executeParrallel(callables);

    logger.info("Size of metric records : {}", metricRecords.size());
    for (NewRelicMetricDataRecord metricRecord : metricRecords) {
      String tag = metricRecord.getTag();
      if (isEmpty(tag)) {
        tag = HARNESS_DEFAULT_TAG;
      }
      if (!observedTimeSeries.containsKey(tag)) {
        observedTimeSeries.put(tag, new ConcurrentHashMap<>());
      }
      Map<String, Map<String, TimeSeriesOfMetric>> txnMetricTimeSeries = observedTimeSeries.get(tag);
      if (!txnMetricTimeSeries.containsKey(metricRecord.getName())) {
        txnMetricTimeSeries.put(metricRecord.getName(), new HashMap<>());
      }
      Map<String, TimeSeriesOfMetric> metricMap = txnMetricTimeSeries.get(metricRecord.getName());
      for (Entry<String, Double> metricData : metricRecord.getValues().entrySet()) {
        final String metricName = metricData.getKey();
        if (!metricMap.containsKey(metricName)) {
          metricMap.put(metricName,
              TimeSeriesOfMetric.builder()
                  .metricName(getDisplayNameOfMetric(metricName))
                  .timeSeries(initializeTimeSeriesDataPointsList(startTime, endTime, TimeUnit.MINUTES.toMillis(1), -1))
                  .risk(-1)
                  .build());
        }

        // fill in the metrics for this record at the correct spots
        metricMap.get(metricName)
            .addToTimeSeriesMap(metricRecord.getDataCollectionMinute(),
                getNormalizedMetricValue(metricName, metricRecord, cvConfiguration.getStateType()));
        metricMap.get(metricName).setMetricType(getMetricType(cvConfiguration, metricName));
        if (isNotEmpty(metricRecord.getDeeplinkMetadata())) {
          if (metricRecord.getDeeplinkUrl().containsKey(metricName)) {
            String deeplinkUrl = metricRecord.getDeeplinkUrl().get(metricName);
            metricMap.get(metricName).setMetricDeeplinkUrl(deeplinkUrl);
          }
        }
      }
    }

    // Find and add the risks for those metrics above
    updateRisksFromSummary(startTime, endTime, riskCutOffTime, cvConfiguration, observedTimeSeries);

    return observedTimeSeries;
  }

  private void filterMetrics(TimeSeriesFilter filter, List<NewRelicMetricDataRecord> records) {
    // filter for metric names
    if (isNotEmpty(filter.getMetricNames())) {
      records.forEach(dataRecord -> {
        for (Iterator<Entry<String, Double>> iterator = dataRecord.getValues().entrySet().iterator();
             iterator.hasNext();) {
          final Entry<String, Double> valueEntry = iterator.next();
          if (!filter.getMetricNames().contains(valueEntry.getKey())) {
            iterator.remove();
          }
        }
      });

      for (Iterator<NewRelicMetricDataRecord> recordIterator = records.iterator(); recordIterator.hasNext();) {
        NewRelicMetricDataRecord dataRecord = recordIterator.next();
        if (isEmpty(dataRecord.getValues())) {
          recordIterator.remove();
        }
      }
    }
  }

  @Override
  public VerificationNodeDataSetupResponse getDataForNode(
      String accountId, String serverConfigId, Object fetchConfig, StateType type) {
    if (fetchConfig == null) {
      throw new VerificationOperationException(ErrorCode.DEFAULT_ERROR_CODE, "Config is null in test");
    }
    try {
      switch (type) {
        case DATA_DOG:
          return getMetricsNodeDataForDatadog(accountId, (DataDogSetupTestNodeData) fetchConfig);
        case DATA_DOG_LOG:
          return getLogNodeDataForDatadog(accountId, (DataDogSetupTestNodeData) fetchConfig);
        case APM_VERIFICATION:
          return getMetricsNodeDataForApmCustom(accountId, (APMSetupTestNodeData) fetchConfig);
        default:
          throw new VerificationOperationException(
              ErrorCode.APM_CONFIGURATION_ERROR, "Invalid StateType provided" + type);
      }
    } catch (Exception e) {
      throw new VerificationOperationException(ErrorCode.APM_CONFIGURATION_ERROR, e.getMessage(), e, USER);
    }
  }

  private VerificationNodeDataSetupResponse getMetricsNodeDataForApmCustom(
      String accountId, APMSetupTestNodeData config) {
    SettingAttribute settingAttribute = settingsService.get(config.getSettingId());
    APMVerificationConfig apmVerificationConfig = (APMVerificationConfig) settingAttribute.getValue();
    APMFetchConfig apmFetchConfig = config.getFetchConfig();
    APMValidateCollectorConfig apmValidateCollectorConfig =
        APMValidateCollectorConfig.builder()
            .baseUrl(apmVerificationConfig.getUrl())
            .headers(apmVerificationConfig.collectionHeaders())
            .options(apmVerificationConfig.collectionParams())
            .url(apmFetchConfig.getUrl())
            .body(apmFetchConfig.getBody())
            .encryptedDataDetails(apmVerificationConfig.encryptedDataDetails(secretManager))
            .build();
    VerificationNodeDataSetupResponse response =
        VerificationNodeDataSetupResponse.builder().providerReachable(false).build();

    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      String apmResponse = delegateProxyFactory.get(APMDelegateService.class, syncTaskContext)
                               .fetch(apmValidateCollectorConfig,
                                   ThirdPartyApiCallLog.createApiCallLog(accountId, apmFetchConfig.getGuid()));
      if (isNotEmpty(apmResponse)) {
        response.setProviderReachable(true);
      }

      Map<String, List<APMMetricInfo>> metricInfoMap =
          buildMetricInfoMap(Arrays.asList(config.getApmMetricCollectionInfo()), null);

      Preconditions.checkState(metricInfoMap.size() == 1);
      List<APMMetricInfo> metricInfoList = metricInfoMap.values().iterator().next();
      Preconditions.checkState(metricInfoList.size() == 1);

      APMResponseParser.APMResponseData responseData =
          new APMResponseParser.APMResponseData(null, DEFAULT_GROUP_NAME, apmResponse, metricInfoList);

      Collection<NewRelicMetricDataRecord> metricDataRecords = APMResponseParser.extract(Arrays.asList(responseData));
      if (isNotEmpty(metricDataRecords)) {
        response.setProviderReachable(true);
        response.setLoadResponse(
            VerificationLoadResponse.builder().isLoadPresent(true).loadResponse(apmResponse).build());
        response.setConfigurationCorrect(true);
        response.setDataForNode(apmFetchConfig);
      }
      return response;
    } catch (Exception ex) {
      logger.error("Exception while parsing the APM Response.");
      response.setConfigurationCorrect(false);
      return response;
    }
  }
  private VerificationNodeDataSetupResponse getMetricsNodeDataForDatadog(
      String accountId, DataDogSetupTestNodeData config) {
    SettingAttribute settingAttribute = settingsService.get(config.getSettingId());
    DatadogConfig datadogConfig = (DatadogConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    APMValidateCollectorConfig apmValidateCollectorConfig = datadogConfig.createAPMValidateCollectorConfig();
    apmValidateCollectorConfig.setEncryptedDataDetails(encryptedDataDetails);
    apmValidateCollectorConfig.getOptions().put("from", String.valueOf(config.getFromTime()));
    apmValidateCollectorConfig.getOptions().put("to", String.valueOf(config.getToTime()));

    Map<String, List<APMMetricInfo>> metricInfoByQuery;
    if (!config.isServiceLevel()) {
      Optional<List<String>> metrics = Optional.empty();
      if (isNotEmpty(config.getMetrics())) {
        metrics = Optional.of(new ArrayList<>(Arrays.asList(config.getMetrics().split(","))));
      }
      metricInfoByQuery = metricEndpointsInfo(Optional.ofNullable(config.getDatadogServiceName()), metrics,
          Optional.empty(), Optional.ofNullable(config.getCustomMetrics()),
          isEmpty(config.getDeploymentType()) ? Optional.empty()
                                              : Optional.of(DeploymentType.valueOf(config.getDeploymentType())));
    } else {
      metricInfoByQuery = createDatadogMetricEndPointMap(
          config.getDockerMetrics(), config.getEcsMetrics(), config.getDatadogServiceName(), config.getCustomMetrics());
    }

    List<Object> loadResponse = new ArrayList<>();

    // loop for each metric
    for (Entry<String, List<APMMetricInfo>> entry : metricInfoByQuery.entrySet()) {
      String url = entry.getKey();
      String hostname = config.getInstanceName();
      if (config.getInstanceElement() != null) {
        hostname = config.getInstanceElement().getHostName();
      }
      if (url.contains(VERIFICATION_HOST_PLACEHOLDER)) {
        url = url.replace(VERIFICATION_HOST_PLACEHOLDER, hostname);
      }
      apmValidateCollectorConfig.setUrl(url);
      VerificationNodeDataSetupResponse verificationNodeDataSetupResponse =
          getVerificationNodeDataResponse(accountId, apmValidateCollectorConfig, config.getGuid());
      APMResponseParser.APMResponseData responseData = new APMResponseParser.APMResponseData(
          null, DEFAULT_GROUP_NAME, (String) verificationNodeDataSetupResponse.getDataForNode(), entry.getValue());

      Collection<NewRelicMetricDataRecord> metricDataRecords = APMResponseParser.extract(Arrays.asList(responseData));

      if (!verificationNodeDataSetupResponse.isProviderReachable()) {
        // if not reachable then directly return. no need to process further
        return VerificationNodeDataSetupResponse.builder().providerReachable(false).build();
      }
      verificationNodeDataSetupResponse.setProviderReachable(true);
      boolean loadPresent = metricDataRecords.size() > 0;

      verificationNodeDataSetupResponse.setLoadResponse(
          VerificationLoadResponse.builder()
              .loadResponse(verificationNodeDataSetupResponse.getDataForNode())
              .isLoadPresent(loadPresent)
              .build());

      // add load response only for metrics containing nodedata.
      if (verificationNodeDataSetupResponse.getLoadResponse().isLoadPresent()) {
        loadResponse.add(verificationNodeDataSetupResponse);
      }
    }

    VerificationLoadResponse response =
        VerificationLoadResponse.builder().loadResponse(loadResponse).isLoadPresent(!isEmpty(loadResponse)).build();

    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(true)
        .loadResponse(response)
        .dataForNode(loadResponse)
        .build();
  }

  private VerificationNodeDataSetupResponse getLogNodeDataForDatadog(
      String accountId, DataDogSetupTestNodeData config) {
    SettingAttribute settingAttribute = settingsService.get(config.getSettingId());
    DatadogConfig datadogConfig = (DatadogConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManager.getEncryptionDetails((EncryptableSetting) settingAttribute.getValue(), null, null);
    APMValidateCollectorConfig apmValidateCollectorConfig = datadogConfig.createLogAPMValidateCollectorConfig();
    apmValidateCollectorConfig.setEncryptedDataDetails(encryptedDataDetails);
    Optional<String> hostName = Optional.empty();
    if (config.getInstanceElement() != null) {
      hostName = Optional.ofNullable(config.getInstanceElement().getHostName());
    }
    long from = Instant.now().minus(17, ChronoUnit.MINUTES).toEpochMilli();
    long to = Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli();
    Map<String, Object> body = createDatadogBodyMapForLogsApi(
        config.getQuery(), from, to, Optional.ofNullable(config.getHostNameField()), hostName);
    apmValidateCollectorConfig.setUrl(DatadogConfig.LOG_API_PATH_SUFFIX);
    apmValidateCollectorConfig.setCollectionMethod(Method.POST);
    apmValidateCollectorConfig.setBody(new JSONObject(body).toString());
    return getVerificationNodeDataResponse(accountId, apmValidateCollectorConfig, config.getGuid());
  }

  private Map<String, Object> createDatadogBodyMapForLogsApi(
      String query, long from, long to, Optional<String> hostNameField, Optional<String> host) {
    Map<String, Object> body = new HashMap<>();
    if (hostNameField.isPresent() && host.isPresent()) {
      body.put("query", String.format("%s:(%s) %s", hostNameField.get(), host.get(), query));
    } else {
      body.put("query", query);
    }
    Map<String, String> timeMap = new HashMap<>();
    timeMap.put("from", String.valueOf(from));
    timeMap.put("to", String.valueOf(to));
    body.put("time", timeMap);
    body.put("limit", 5);
    return body;
  }

  @Override
  public boolean notifyVerificationState(String correlationId, VerificationDataAnalysisResponse response) {
    try {
      logger.info("Received state notification for {} data {}", correlationId, response);
      waitNotifyEngine.notify(correlationId, response);
      return true;
    } catch (Exception ex) {
      logger.error("Exception while notifying correlationId {}", correlationId, ex);
      return false;
    }
  }

  @Override
  public boolean notifyWorkflowVerificationState(String appId, String stateExecutionId, ExecutionStatus status) {
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateExecutionId, stateExecutionId)
                                                .get();
    if (analysisContext == null) {
      logger.error("for app {} could not find context for {}", appId, stateExecutionId);
      return false;
    }
    final User user = UserThreadLocal.get();
    String message = user == null
        ? "The state was marked " + status.name().toLowerCase()
        : "The state was marked " + status.name().toLowerCase() + " by " + user.getName() + "(" + user.getEmail() + ")";
    if (status == ExecutionStatus.SUCCESS) {
      cvActivityLogService.getLoggerByStateExecutionId(stateExecutionId).info(message);
    } else {
      cvActivityLogService.getLoggerByStateExecutionId(stateExecutionId).error(message);
    }
    if (getLogAnalysisStates().contains(analysisContext.getStateType())) {
      final LogMLAnalysisRecord analysisRecord = LogMLAnalysisRecord.builder()
                                                     .logCollectionMinute(-1)
                                                     .stateType(analysisContext.getStateType())
                                                     .appId(appId)
                                                     .stateExecutionId(stateExecutionId)
                                                     .query(analysisContext.getQuery())
                                                     .analysisSummaryMessage(message)
                                                     .control_events(Collections.emptyMap())
                                                     .test_events(Collections.emptyMap())
                                                     .logCollectionMinute(analysisContext.getTimeDuration())
                                                     .build();

      analysisRecord.setAnalysisStatus(
          featureFlagService.isEnabled(FeatureName.CV_FEEDBACKS, appService.getAccountIdByAppId(appId))
              ? LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE
              : LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
      wingsPersistence.saveIgnoringDuplicateKeys(Lists.newArrayList(analysisRecord));
    } else if (getMetricAnalysisStates().contains(analysisContext.getStateType())) {
      NewRelicMetricAnalysisRecord metricAnalysisRecord =
          NewRelicMetricAnalysisRecord.builder()
              .message(message)
              .appId(appId)
              .stateType(analysisContext.getStateType())
              .stateExecutionId(stateExecutionId)
              .workflowExecutionId(analysisContext.getWorkflowExecutionId())
              .analysisMinute(analysisContext.getTimeDuration())
              .build();
      wingsPersistence.saveIgnoringDuplicateKeys(Lists.newArrayList(metricAnalysisRecord));
    } else {
      throw new WingsException("Invalid state type :" + analysisContext.getStateType());
    }
    continuousVerificationService.setMetaDataExecutionStatus(stateExecutionId, status, true);
    try {
      final VerificationStateAnalysisExecutionData stateAnalysisExecutionData =
          VerificationStateAnalysisExecutionData.builder()
              .appId(appId)
              .correlationId(analysisContext.getCorrelationId())
              .workflowExecutionId(analysisContext.getWorkflowExecutionId())
              .stateExecutionInstanceId(stateExecutionId)
              .serverConfigId(analysisContext.getAnalysisServerConfigId())
              .timeDuration(analysisContext.getTimeDuration())
              .canaryNewHostNames(analysisContext.getTestNodes() != null
                      ? new HashSet<>(analysisContext.getTestNodes().keySet())
                      : null)
              .lastExecutionNodes(analysisContext.getControlNodes() != null
                      ? new HashSet<>(analysisContext.getControlNodes().keySet())
                      : null)
              .delegateTaskId(analysisContext.getDelegateTaskId())
              .mlAnalysisType(analysisContext.getAnalysisType())
              .query(analysisContext.getQuery())
              .build();
      stateAnalysisExecutionData.setStatus(status);

      final VerificationDataAnalysisResponse analysisResponse =
          VerificationDataAnalysisResponse.builder().stateExecutionData(stateAnalysisExecutionData).build();
      analysisResponse.setExecutionStatus(status);

      logger.info("for {} user triggered notification, data {}", stateExecutionId, analysisResponse);
      final String notificationId = waitNotifyEngine.notify(analysisContext.getCorrelationId(), analysisResponse);
      logger.info("for {} user triggered notification, notification id", stateExecutionId, notificationId);
      return true;
    } catch (Exception ex) {
      logger.error("Exception for {} while notifying correlationId {}", stateExecutionId,
          analysisContext.getStateExecutionId(), ex);
      return false;
    }
  }

  @Override
  public boolean collect247Data(String cvConfigId, StateType stateType, long startTime, long endTime) {
    String waitId = generateUuid();
    DelegateTask task;
    CVConfiguration cvConfiguration =
        wingsPersistence.createQuery(CVConfiguration.class).filter("_id", cvConfigId).get();
    switch (stateType) {
      case APP_DYNAMICS:
        AppDynamicsCVServiceConfiguration config = (AppDynamicsCVServiceConfiguration) cvConfiguration;
        task = createAppDynamicsDelegateTask(config, waitId, startTime, endTime);
        break;
      case NEW_RELIC:
        NewRelicCVServiceConfiguration nrConfig = (NewRelicCVServiceConfiguration) cvConfiguration;
        task = createNewRelicDelegateTask(nrConfig, waitId, startTime, endTime);
        break;
      case DYNA_TRACE:
        DynaTraceCVServiceConfiguration dynaTraceCVServiceConfiguration =
            (DynaTraceCVServiceConfiguration) cvConfiguration;
        task = createDynaTraceDelegateTask(dynaTraceCVServiceConfiguration, waitId, startTime, endTime);
        break;
      case PROMETHEUS:
        PrometheusCVServiceConfiguration prometheusCVServiceConfiguration =
            (PrometheusCVServiceConfiguration) cvConfiguration;
        task = createPrometheusDelegateTask(prometheusCVServiceConfiguration, waitId, startTime, endTime);
        break;
      case DATA_DOG:
        DatadogCVServiceConfiguration ddConfig = (DatadogCVServiceConfiguration) cvConfiguration;
        task = createDatadogDelegateTask(ddConfig, waitId, startTime, endTime);
        break;
      case CLOUD_WATCH:
        CloudWatchCVServiceConfiguration cloudWatchCVServiceConfiguration =
            (CloudWatchCVServiceConfiguration) cvConfiguration;
        task = createCloudWatchDelegateTask(cloudWatchCVServiceConfiguration, waitId, startTime, endTime);
        break;
      case SUMO:
      case DATA_DOG_LOG:
        LogsCVConfiguration logsCVConfiguration = (LogsCVConfiguration) cvConfiguration;
        task = createDataCollectionDelegateTask(logsCVConfiguration, waitId, startTime, endTime);
        break;
      case ELK:
        ElkCVConfiguration elkCVConfiguration = (ElkCVConfiguration) cvConfiguration;
        task = createElkDelegateTask(elkCVConfiguration, waitId, startTime, endTime);
        break;
      case SPLUNKV2:
        SplunkCVConfiguration splunkCVConfiguration = (SplunkCVConfiguration) cvConfiguration;
        task = createSplunkDelegateTask(splunkCVConfiguration, waitId, startTime, endTime);
        break;
      case BUG_SNAG:
        BugsnagCVConfiguration bugsnagCVConfiguration = (BugsnagCVConfiguration) cvConfiguration;
        task = createBugSnagDelegateTask(bugsnagCVConfiguration, waitId, startTime, endTime);
        break;
      case STACK_DRIVER_LOG:
        StackdriverCVConfiguration stackdriverCVConfiguration = (StackdriverCVConfiguration) cvConfiguration;
        task = createDataCollectionDelegateTask(stackdriverCVConfiguration, waitId, startTime, endTime);
        break;
      case APM_VERIFICATION:
        APMCVServiceConfiguration apmcvServiceConfiguration = (APMCVServiceConfiguration) cvConfiguration;
        task = createAPMDelegateTask(apmcvServiceConfiguration, waitId, startTime, endTime);
        break;
      default:
        logger.error("Calling collect 24x7 data for an unsupported state : {}", stateType);
        return false;
    }
    waitNotifyEngine.waitForAll(DataCollectionCallback.builder()
                                    .appId(cvConfiguration.getAppId())
                                    .executionData(getExecutionData(cvConfiguration, waitId,
                                        (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime)))
                                    .cvConfigId(cvConfiguration.getUuid())
                                    .dataCollectionStartTime(startTime)
                                    .dataCollectionEndTime(endTime)
                                    .build(),
        waitId);
    logger.info("Queuing 24x7 data collection task for {}, cvConfigurationId: {}", stateType, cvConfigId);
    cvActivityLogService.getLoggerByCVConfigId(cvConfiguration.getUuid(), TimeUnit.MILLISECONDS.toMinutes(endTime))
        .info("Submitting service guard data collection task for time range %t to %t.", startTime, endTime);
    delegateService.queueTask(task);
    return true;
  }

  @Override
  public boolean collectCVDataForWorkflow(String contextId, long collectionMinute) {
    AnalysisContext context = wingsPersistence.createQuery(AnalysisContext.class).filter("_id", contextId).get();
    logger.info("Trigger Data Collection for workflow with stateType {}, stateExecutionId {}, CollectionMinute {}",
        context.getStateType(), context.getStateExecutionId(), collectionMinute);
    switch (context.getStateType()) {
      case SUMO:
      case ELK:
      case DATA_DOG_LOG:
      case STACK_DRIVER_LOG:
        return createDataCollectionDelegateTask(context, collectionMinute);
      default:
        logger.error("Calling collect data for an unsupported state");
        return false;
    }
  }

  @Override
  public boolean collectCVData(String cvTaskId) {
    CVTask cvTask = cvTaskService.getCVTask(cvTaskId);
    DelegateTask delegateTask = createDataCollectionDelegateTask(cvTask);
    delegateService.queueTask(delegateTask);
    return true;
  }

  @Override
  public boolean createCVTask247(String cvConfigId, Instant startTime, Instant endTime) {
    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    DataCollectionInfoV2 dataCollectionInfo = dataCollectionInfoService.create(cvConfiguration, startTime, endTime);
    CVTask cvTask = CVTask.builder()
                        .status(ExecutionStatus.QUEUED)
                        .cvConfigId(cvConfiguration.getUuid())
                        .accountId(cvConfiguration.getAccountId())
                        .dataCollectionInfo(dataCollectionInfo)
                        .build();
    cvTaskService.saveCVTask(cvTask);
    return false;
  }

  private StateExecutionData getExecutionData(CVConfiguration cvConfiguration, String waitId, int timeDuration) {
    return VerificationStateAnalysisExecutionData.builder()
        .appId(cvConfiguration.getAppId())
        .workflowExecutionId(null)
        .stateExecutionInstanceId(CV_24x7_STATE_EXECUTION + "-" + cvConfiguration.getUuid())
        .serverConfigId(cvConfiguration.getConnectorId())
        .timeDuration(timeDuration)
        .canaryNewHostNames(new HashSet<>())
        .lastExecutionNodes(new HashSet<>())
        .correlationId(waitId)
        .build();
  }

  private DelegateTask createDynaTraceDelegateTask(
      DynaTraceCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    DynaTraceConfig dynaTraceConfig = (DynaTraceConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    final DynaTraceDataCollectionInfo dataCollectionInfo =
        DynaTraceDataCollectionInfo.builder()
            .dynaTraceConfig(dynaTraceConfig)
            .applicationId(config.getAppId())
            .serviceId(config.getServiceId())
            .cvConfigId(config.getUuid())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .timeSeriesDefinitions(Lists.newArrayList(DynaTraceTimeSeries.values()))
            .serviceMethods(DynatraceState.splitServiceMethods(config.getServiceMethods()))
            .startTime(startTime)
            .collectionTime(timeDuration)
            .dataCollectionMinute(0)
            .encryptedDataDetails(secretManager.getEncryptionDetails(dynaTraceConfig, config.getAppId(), null))
            .analysisComparisonStrategy(AnalysisComparisonStrategy.PREDICTIVE)
            .build();

    return createDelegateTask(TaskType.DYNATRACE_COLLECT_24_7_METRIC_DATA, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createAppDynamicsDelegateTask(
      AppDynamicsCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    final AppdynamicsDataCollectionInfo dataCollectionInfo =
        AppdynamicsDataCollectionInfo.builder()
            .appDynamicsConfig(appDynamicsConfig)
            .applicationId(config.getAppId())
            .serviceId(config.getServiceId())
            .cvConfigId(config.getUuid())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .startTime(startTime)
            .collectionTime(timeDuration)
            .appId(Long.parseLong(config.getAppDynamicsApplicationId()))
            .tierId(Long.parseLong(config.getTierId()))
            .dataCollectionMinute(0)
            .hosts(new HashMap<>())
            .encryptedDataDetails(secretManager.getEncryptionDetails(appDynamicsConfig, config.getAppId(), null))
            .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
            .build();
    return createDelegateTask(TaskType.APPDYNAMICS_COLLECT_24_7_METRIC_DATA, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createBugSnagDelegateTask(
      BugsnagCVConfiguration config, String waitId, long startTime, long endTime) {
    BugsnagConfig bugsnagConfig = (BugsnagConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    final CustomLogDataCollectionInfo dataCollectionInfo =
        CustomLogDataCollectionInfo.builder()
            .baseUrl(bugsnagConfig.getUrl())
            .validationUrl(BugsnagConfig.validationUrl)
            .cvConfidId(config.getUuid())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .headers(bugsnagConfig.headersMap())
            .options(bugsnagConfig.optionsMap())
            .query(config.getQuery())
            .encryptedDataDetails(secretManager.getEncryptionDetails(bugsnagConfig, config.getAppId(), null))
            .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
            .stateType(StateType.BUG_SNAG)
            .applicationId(config.getAppId())
            .serviceId(config.getServiceId())
            .startTime(startTime)
            .endTime(endTime)
            .startMinute(0)
            .responseDefinition(BugsnagState.constructLogDefinitions(config.getProjectId(), config.getReleaseStage()))
            .shouldInspectHosts(!config.isBrowserApplication())
            .collectionFrequency(1)
            .collectionTime(timeDuration)
            .accountId(config.getAccountId())
            .build();
    return createDelegateTask(TaskType.CUSTOM_COLLECT_24_7_LOG_DATA, config.getAccountId(), config.getAppId(), waitId,
        new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createNewRelicDelegateTask(
      NewRelicCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    final NewRelicConfig newRelicConfig = (NewRelicConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    final NewRelicDataCollectionInfo dataCollectionInfo =
        NewRelicDataCollectionInfo.builder()
            .newRelicConfig(newRelicConfig)
            .applicationId(config.getAppId())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .serviceId(config.getServiceId())
            .startTime(startTime)
            .cvConfigId(config.getUuid())
            .collectionTime(timeDuration)
            .newRelicAppId(Long.parseLong(config.getApplicationId()))
            .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
            .dataCollectionMinute(0)
            .hosts(hostsMap)
            .encryptedDataDetails(secretManager.getEncryptionDetails(newRelicConfig, config.getAppId(), null))
            .settingAttributeId(config.getConnectorId())
            .checkNotAllowedStrings(!featureFlagService.isEnabled(
                FeatureName.DISABLE_METRIC_NAME_CURLY_BRACE_CHECK, newRelicConfig.getAccountId()))
            .build();
    return createDelegateTask(TaskType.NEWRELIC_COLLECT_24_7_METRIC_DATA, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createPrometheusDelegateTask(
      PrometheusCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    PrometheusConfig prometheusConfig = (PrometheusConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    final PrometheusDataCollectionInfo dataCollectionInfo =
        PrometheusDataCollectionInfo.builder()
            .prometheusConfig(prometheusConfig)
            .applicationId(config.getAppId())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .serviceId(config.getServiceId())
            .cvConfigId(config.getUuid())
            .startTime(startTime)
            .collectionTime(timeDuration)
            .timeSeriesToCollect(renderFetchQueries(config.getTimeSeriesToAnalyze()))
            .hosts(new HashMap<>())
            .timeSeriesMlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
            .dataCollectionMinute(0)
            .build();
    return createDelegateTask(TaskType.PROMETHEUS_COLLECT_24_7_METRIC_DATA, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createDatadogDelegateTask(
      DatadogCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    DatadogConfig datadogConfig = (DatadogConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);

    Map<String, List<APMMetricInfo>> metricEndPoints = createDatadogMetricEndPointMap(
        config.getDockerMetrics(), config.getEcsMetrics(), config.getDatadogServiceName(), config.getCustomMetrics());

    final APMDataCollectionInfo dataCollectionInfo =
        APMDataCollectionInfo.builder()
            .baseUrl(datadogConfig.getUrl())
            .validationUrl(DatadogConfig.validationUrl)
            .encryptedDataDetails(secretManager.getEncryptionDetails(datadogConfig, config.getAppId(), null))
            .hosts(hostsMap)
            .stateType(StateType.DATA_DOG)
            .applicationId(config.getAppId())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .serviceId(config.getServiceId())
            .startTime(startTime)
            .cvConfigId(config.getUuid())
            .dataCollectionMinute(0)
            .metricEndpoints(metricEndPoints)
            .accountId(config.getAccountId())
            .strategy(AnalysisComparisonStrategy.PREDICTIVE)
            .dataCollectionFrequency(1)
            .dataCollectionTotalTime(timeDuration)
            .build();
    return createDelegateTask(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createAPMDelegateTask(
      APMCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    APMVerificationConfig apmVerificationConfig =
        (APMVerificationConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);

    Map<String, List<APMMetricInfo>> metricEndPoints =
        buildMetricInfoMap(config.getMetricCollectionInfos(), Optional.empty());

    final APMDataCollectionInfo dataCollectionInfo =
        APMDataCollectionInfo.builder()
            .baseUrl(apmVerificationConfig.getUrl())
            .headers(apmVerificationConfig.collectionHeaders())
            .options(apmVerificationConfig.collectionParams())
            .validationUrl(apmVerificationConfig.getValidationUrl())
            .encryptedDataDetails(apmVerificationConfig.encryptedDataDetails(secretManager))
            .hosts(hostsMap)
            .stateType(StateType.APM_VERIFICATION)
            .applicationId(config.getAppId())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .serviceId(config.getServiceId())
            .startTime(startTime)
            .cvConfigId(config.getUuid())
            .dataCollectionMinute(0)
            .metricEndpoints(metricEndPoints)
            .accountId(config.getAccountId())
            .strategy(AnalysisComparisonStrategy.PREDICTIVE)
            .dataCollectionFrequency(1)
            .dataCollectionTotalTime(timeDuration)
            .build();

    return createDelegateTask(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  public static Map<String, List<APMMetricInfo>> createDatadogMetricEndPointMap(Map<String, String> dockerMetricsMap,
      Map<String, String> ecsMetricsMap, String datadogServiceName, Map<String, Set<Metric>> customMetricsMap) {
    Map<String, List<APMMetricInfo>> metricEndPoints = new HashMap<>();
    if (isNotEmpty(dockerMetricsMap)) {
      for (Entry<String, String> entry : dockerMetricsMap.entrySet()) {
        String filter = entry.getKey();
        List<String> dockerMetrics = new ArrayList<>(Arrays.asList(entry.getValue().split(","))
                                                         .parallelStream()
                                                         .map(String ::trim)
                                                         .collect(Collectors.toList()));
        metricEndPoints.putAll(DatadogState.metricEndpointsInfo(
            Optional.empty(), Optional.of(dockerMetrics), Optional.of(filter), Optional.empty(), Optional.empty()));
      }
    }
    if (isNotEmpty(ecsMetricsMap)) {
      for (Entry<String, String> entry : ecsMetricsMap.entrySet()) {
        String filter = entry.getKey();
        List<String> ecsMetrics = new ArrayList<>(Arrays.asList(entry.getValue().split(","))
                                                      .parallelStream()
                                                      .map(String ::trim)
                                                      .collect(Collectors.toList()));
        metricEndPoints.putAll(DatadogState.metricEndpointsInfo(
            Optional.empty(), Optional.of(ecsMetrics), Optional.of(filter), Optional.empty(), Optional.empty()));
      }
    }
    metricEndPoints.putAll(DatadogState.metricEndpointsInfo(Optional.ofNullable(datadogServiceName), Optional.empty(),
        Optional.empty(), Optional.ofNullable(customMetricsMap), Optional.empty()));

    return metricEndPoints;
  }

  private DelegateTask createCloudWatchDelegateTask(
      CloudWatchCVServiceConfiguration config, String waitId, long startTime, long endTime) {
    AwsConfig awsConfig = (AwsConfig) settingsService.get(config.getConnectorId()).getValue();
    int timeDuration = (int) TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);

    final CloudWatchDataCollectionInfo dataCollectionInfo =
        CloudWatchDataCollectionInfo.builder()
            .awsConfig(awsConfig)
            .applicationId(config.getAppId())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .serviceId(config.getServiceId())
            .cvConfigId(config.getUuid())
            .startTime(startTime)
            .collectionTime(timeDuration)
            .hosts(cloudWatchService.getGroupNameByHost(config.getEc2InstanceNames()))
            .ec2Metrics(config.getEc2Metrics())
            .encryptedDataDetails(secretManager.getEncryptionDetails(awsConfig, config.getAppId(), null))
            .analysisComparisonStrategy(AnalysisComparisonStrategy.PREDICTIVE)
            .loadBalancerMetrics(config.getLoadBalancerMetrics())
            .lambdaFunctionNames(config.getLambdaFunctionsMetrics())
            .metricsByECSClusterName(config.getEcsMetrics())
            .region(config.getRegion())
            .dataCollectionMinute(0)
            .build();
    return createDelegateTask(TaskType.CLOUD_WATCH_COLLECT_24_7_METRIC_DATA, config.getAccountId(), config.getAppId(),
        waitId, new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createElkDelegateTask(ElkCVConfiguration config, String waitId, long startTime, long endTime) {
    ElkDataCollectionInfo dataCollectionInfo;
    if (config.isWorkflowConfig()) {
      // Extract data collection info from Analysis Context
      dataCollectionInfo = (ElkDataCollectionInfo) wingsPersistence.get(AnalysisContext.class, config.getContextId())
                               .getDataCollectionInfo();
    } else {
      ElkConfig elkConfig = (ElkConfig) settingsService.get(config.getConnectorId()).getValue();
      dataCollectionInfo =
          ElkDataCollectionInfo.builder()
              .elkConfig(elkConfig)
              .accountId(elkConfig.getAccountId())
              .applicationId(config.getAppId())
              .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
              .serviceId(config.getServiceId())
              .query(config.getQuery())
              .indices(config.getIndex())
              .hostnameField(config.getHostnameField())
              .messageField(config.getMessageField())
              .timestampField(config.getTimestampField())
              .timestampFieldFormat(config.getTimestampFormat())
              .queryType(config.getQueryType())
              .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
              .encryptedDataDetails(secretManager.getEncryptionDetails(elkConfig, config.getAppId(), null))
              .build();
    }
    dataCollectionInfo.setCvConfigId(config.getUuid());
    dataCollectionInfo.setStartTime(startTime);
    dataCollectionInfo.setEndTime(endTime);
    return createDelegateTask(TaskType.ELK_COLLECT_24_7_LOG_DATA, config.getAccountId(), config.getAppId(), waitId,
        new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createSplunkDelegateTask(
      SplunkCVConfiguration config, String waitId, long startTimeMilliSec, long endTimeMilliSec) {
    SplunkConfig splunkConfig = (SplunkConfig) settingsService.get(config.getConnectorId()).getValue();
    SplunkDataCollectionInfo dataCollectionInfo =
        SplunkDataCollectionInfo.builder()
            .splunkConfig(splunkConfig)
            .accountId(splunkConfig.getAccountId())
            .applicationId(config.getAppId())
            .stateExecutionId(CV_24x7_STATE_EXECUTION + "-" + config.getUuid())
            .serviceId(config.getServiceId())
            .query(config.getQuery())
            .hostnameField(config.getHostnameField())
            .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
            .encryptedDataDetails(secretManager.getEncryptionDetails(splunkConfig, config.getAppId(), null))
            .build();

    dataCollectionInfo.setCvConfigId(config.getUuid());
    dataCollectionInfo.setStartTime(startTimeMilliSec);
    dataCollectionInfo.setEndTime(endTimeMilliSec);
    return createDelegateTask(TaskType.SPLUNK_COLLECT_24_7_LOG_DATA, config.getAccountId(), config.getAppId(), waitId,
        new Object[] {dataCollectionInfo}, config.getEnvId());
  }

  private DelegateTask createDataCollectionDelegateTask(CVTask cvTask) {
    DataCollectionInfoV2 dataCollectionInfo = cvTask.getDataCollectionInfo();
    DelegateTask delegateTask = createDelegateTask(dataCollectionInfo.getTaskType(), dataCollectionInfo.getAccountId(),
        dataCollectionInfo.getApplicationId(), cvTask.getUuid(), new Object[] {dataCollectionInfo},
        dataCollectionInfo.getEnvId());
    waitNotifyEngine.waitForAll(
        DataCollectionCallbackV2.builder().cvTaskId(cvTask.getUuid()).build(), cvTask.getUuid());

    return delegateTask;
  }
  private DelegateTask createDataCollectionDelegateTask(
      LogsCVConfiguration config, String waitId, long startTime, long endTime) {
    String stateExecutionId = CV_24x7_STATE_EXECUTION + "-" + config.getUuid();
    long duration = TimeUnit.MILLISECONDS.toMinutes(endTime - startTime);
    logger.info("Created data collection delegate task for config : {} startTime : {} endTime : {} duration: {}",
        config, startTime, endTime, duration);
    if (config.isWorkflowConfig()) {
      stateExecutionId = wingsPersistence.get(AnalysisContext.class, config.getContextId()).getStateExecutionId();
    }
    switch (config.getStateType()) {
      case SUMO:
        SumoConfig sumoConfig = (SumoConfig) settingsService.get(config.getConnectorId()).getValue();
        final SumoDataCollectionInfo dataCollectionInfo =
            SumoDataCollectionInfo.builder()
                .sumoConfig(sumoConfig)
                .accountId(sumoConfig.getAccountId())
                .applicationId(config.getAppId())
                .stateExecutionId(stateExecutionId)
                .cvConfigId(config.getUuid())
                .serviceId(config.getServiceId())
                .query(config.getQuery())
                .startTime(startTime)
                .endTime(endTime)
                .encryptedDataDetails(secretManager.getEncryptionDetails(sumoConfig, config.getAppId(), null))
                .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
                .build();
        return createDelegateTask(TaskType.SUMO_COLLECT_24_7_LOG_DATA, config.getAccountId(), config.getAppId(), waitId,
            new Object[] {dataCollectionInfo}, config.getEnvId());

      case DATA_DOG_LOG:
        DatadogConfig datadogConfig = (DatadogConfig) settingsService.get(config.getConnectorId()).getValue();
        final CustomLogDataCollectionInfo customLogDataCollectionInfo =
            CustomLogDataCollectionInfo.builder()
                .baseUrl(datadogConfig.getUrl())
                .validationUrl(DatadogConfig.validationUrl)
                .dataUrl(DatadogConfig.LOG_API_PATH_SUFFIX)
                .headers(new HashMap<>())
                .options(datadogConfig.fetchLogOptionsMap())
                .query(config.getQuery())
                .body(datadogConfig.fetchLogBodyMap(true))
                .encryptedDataDetails(secretManager.getEncryptionDetails(datadogConfig, config.getAppId(), null))
                .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
                .stateType(StateType.DATA_DOG_LOG)
                .applicationId(config.getAppId())
                .serviceId(config.getServiceId())
                .stateExecutionId(stateExecutionId)
                .startTime(startTime)
                .endTime(endTime)
                .accountId(config.getAccountId())
                .cvConfidId(config.getUuid())
                .responseDefinition(DatadogLogState.constructLogDefinitions(datadogConfig, null, true))
                .build();
        return createDelegateTask(TaskType.CUSTOM_COLLECT_24_7_LOG_DATA, config.getAccountId(), config.getAppId(),
            waitId, new Object[] {customLogDataCollectionInfo}, config.getEnvId());
      case STACK_DRIVER_LOG:
        StackdriverCVConfiguration stackdriverCVConfiguration = (StackdriverCVConfiguration) config;
        GcpConfig gcpConfig = (GcpConfig) settingsService.get(config.getConnectorId()).getValue();
        if (stackdriverCVConfiguration.isLogsConfiguration()) {
          StackDriverLogDataCollectionInfo stackDriverLogDataCollectionInfo =
              StackDriverLogDataCollectionInfo.builder()
                  .gcpConfig(gcpConfig)
                  .accountId(config.getAccountId())
                  .applicationId(config.getAppId())
                  .logMessageField(stackdriverCVConfiguration.getMessageField())
                  .stateExecutionId(stateExecutionId)
                  .serviceId(config.getServiceId())
                  .query(stackdriverCVConfiguration.getQuery())
                  .startTime(startTime)
                  .endTime(endTime)
                  .hostnameField(stackdriverCVConfiguration.getHostnameField())
                  .collectionTime((int) duration)
                  .hosts(Sets.newHashSet(DUMMY_HOST_NAME))
                  .cvConfigId(config.getUuid())
                  .stateType(StateType.STACK_DRIVER_LOG)
                  .encryptedDataDetails(
                      secretManager.getEncryptionDetails(gcpConfig, stackdriverCVConfiguration.getAppId(), null))
                  .build();
          return createDelegateTask(TaskType.STACKDRIVER_COLLECT_24_7_LOG_DATA, config.getAccountId(),
              config.getAppId(), waitId, new Object[] {stackDriverLogDataCollectionInfo}, config.getEnvId());
        } else {
          throw new IllegalArgumentException("Stackdriver timeseries not implemented yet");
        }

      default:
        throw new IllegalStateException("Invalid state: " + config.getStateType());
    }
  }

  private boolean createDataCollectionDelegateTask(AnalysisContext context, long collectionStartMinute) {
    Map<String, String> canaryNewHostNames = context.getTestNodes();
    Map<String, String> lastExecutionNodes = context.getControlNodes();
    Map<String, String> hostsToBeCollected = new HashMap<>();

    if (context.getComparisonStrategy() == AnalysisComparisonStrategy.COMPARE_WITH_CURRENT
        && lastExecutionNodes != null) {
      hostsToBeCollected.putAll(lastExecutionNodes);
    }
    hostsToBeCollected.putAll(canaryNewHostNames);

    VerificationStateAnalysisExecutionData executionData =
        createLogAnalysisExecutionData(context, canaryNewHostNames.keySet(), lastExecutionNodes);
    List<DelegateTask> delegateTasks = new ArrayList<>();
    List<String> hostList =
        hostsToBeCollected.keySet().stream().map(Misc::replaceUnicodeWithDot).collect(Collectors.toList());
    switch (context.getStateType()) {
      case SUMO:
        try {
          for (List<String> hostBatch : Lists.partition(hostList, HOST_BATCH_SIZE)) {
            final LogDataCollectionInfo dataCollectionInfo =
                createLogDataCollectionInfo(context, collectionStartMinute, new HashSet<>(hostBatch));
            delegateTasks.add(
                createDelegateTaskAndNotify(dataCollectionInfo, SUMO_COLLECT_LOG_DATA, executionData, context, true));
          }
          for (DelegateTask task : delegateTasks) {
            delegateService.queueTask(task);
          }
        } catch (Exception ex) {
          throw new WingsException("log analysis state failed ", ex);
        }
        break;
      case ELK:
        try {
          for (List<String> hostBatch : Lists.partition(hostList, HOST_BATCH_SIZE)) {
            final LogDataCollectionInfo dataCollectionInfo =
                createLogDataCollectionInfo(context, collectionStartMinute, new HashSet<>(hostBatch));
            delegateTasks.add(
                createDelegateTaskAndNotify(dataCollectionInfo, ELK_COLLECT_LOG_DATA, executionData, context, true));
          }
          for (DelegateTask task : delegateTasks) {
            delegateService.queueTask(task);
          }
        } catch (Exception ex) {
          throw new WingsException("log analysis state failed ", ex);
        }
        break;
      case DATA_DOG_LOG:
        try {
          DatadogConfig datadogConfig =
              (DatadogConfig) settingsService.get(context.getAnalysisServerConfigId()).getValue();
          for (List<String> hostBatch : Lists.partition(hostList, HOST_BATCH_SIZE)) {
            final CustomLogDataCollectionInfo dataCollectionInfo = createCustomLogDataCollectionInfo(
                datadogConfig, context, collectionStartMinute, new HashSet<>(hostBatch));
            delegateTasks.add(createDelegateTaskAndNotify(
                dataCollectionInfo, CUSTOM_LOG_COLLECTION_TASK, executionData, context, true));
          }
          for (DelegateTask task : delegateTasks) {
            delegateService.queueTask(task);
          }
        } catch (Exception ex) {
          throw new WingsException("log analysis state failed ", ex);
        }
        break;
      case STACK_DRIVER_LOG:
        try {
          GcpConfig gcpConfig = (GcpConfig) settingsService.get(context.getAnalysisServerConfigId()).getValue();
          for (List<String> hostBatch : Lists.partition(hostList, HOST_BATCH_SIZE)) {
            final StackDriverLogDataCollectionInfo dataCollectionInfo = createStackDriverLogDataCollectionInfo(
                gcpConfig, context, collectionStartMinute, new HashSet<>(hostBatch));
            delegateTasks.add(createDelegateTaskAndNotify(
                dataCollectionInfo, STACKDRIVER_COLLECT_LOG_DATA, executionData, context, true));
          }
          for (DelegateTask task : delegateTasks) {
            delegateService.queueTask(task);
          }
        } catch (Exception ex) {
          throw new WingsException("log analysis state failed ", ex);
        }
        break;
      default:
        throw new IllegalStateException("Invalid state: " + context.getStateType());
    }
    return true;
  }

  private DelegateTask createDelegateTaskAndNotify(DataCollectionInfo dataCollectionInfo, TaskType taskType,
      VerificationStateAnalysisExecutionData executionData, AnalysisContext context,
      boolean isDataCollectionPerMinuteTask) {
    String waitId = generateUuid();
    long startTime;
    long endTime;
    // this is a deprecated way of collecting data so we will get rid of this once new data collection framework is
    // implemented for these data collectors.
    switch (context.getStateType()) {
      case SUMO:
      case ELK:
      case DATA_DOG_LOG:
        LogDataCollectionInfo logDataCollectionInfo = (LogDataCollectionInfo) dataCollectionInfo;
        startTime = logDataCollectionInfo.getStartTime();
        endTime = logDataCollectionInfo.getEndTime();
        break;
      case STACK_DRIVER_LOG:
        // unfortunately we have inconsistency in the way we set startTime and endTime between providers for per minute
        // task but per minute task is soon going to be replaced by new data collection framework so putting this
        // special condition to to fix activity logs.
        logDataCollectionInfo = (LogDataCollectionInfo) dataCollectionInfo;
        startTime = TimeUnit.MILLISECONDS.toMinutes(logDataCollectionInfo.getStartTime());
        endTime = TimeUnit.MILLISECONDS.toMinutes(logDataCollectionInfo.getEndTime());
        break;
      default:
        throw new IllegalStateException("Invalid state: " + context.getStateType());
    }
    DelegateTask delegateTask = createDelegateTask(taskType, context.getAccountId(), context.getAppId(), waitId,
        new Object[] {dataCollectionInfo}, context.getEnvId());
    waitNotifyEngine.waitForAll(DataCollectionCallback.builder()
                                    .appId(context.getAppId())
                                    .executionData(executionData)
                                    .isDataCollectionPerMinuteTask(isDataCollectionPerMinuteTask)
                                    .dataCollectionStartTime(TimeUnit.MINUTES.toMillis(startTime))
                                    .dataCollectionEndTime(TimeUnit.MINUTES.toMillis(endTime))
                                    .stateExecutionId(context.getStateExecutionId())
                                    .stateType(context.getStateType())
                                    .build(),
        waitId);
    return delegateTask;
  }

  private LogDataCollectionInfo createLogDataCollectionInfo(
      AnalysisContext context, long collectionStartMinute, Set<String> hostBatch) {
    LogDataCollectionInfo savedDataCollectionInfo = (LogDataCollectionInfo) context.getDataCollectionInfo();
    savedDataCollectionInfo.setHosts(hostBatch);
    savedDataCollectionInfo.setStartMinute((int) collectionStartMinute);
    savedDataCollectionInfo.setStartTime(collectionStartMinute);
    savedDataCollectionInfo.setEndTime(collectionStartMinute + 1);
    savedDataCollectionInfo.setCollectionTime(1);
    switch (savedDataCollectionInfo.getStateType()) {
      case ELK:
        savedDataCollectionInfo.setEncryptedDataDetails(
            secretManager.getEncryptionDetails(((ElkDataCollectionInfo) savedDataCollectionInfo).getElkConfig(),
                context.getAppId(), context.getWorkflowExecutionId()));
        break;
      case SUMO:
        savedDataCollectionInfo.setEncryptedDataDetails(
            secretManager.getEncryptionDetails(((SumoDataCollectionInfo) savedDataCollectionInfo).getSumoConfig(),
                context.getAppId(), context.getWorkflowExecutionId()));
        break;
      default:
        unhandled(savedDataCollectionInfo.getStateType());
    }
    return savedDataCollectionInfo;
  }

  private CustomLogDataCollectionInfo createCustomLogDataCollectionInfo(
      DatadogConfig datadogConfig, AnalysisContext context, long collectionStartMinute, Set<String> hostBatch) {
    CustomLogDataCollectionInfo savedDataCollectionInfo = (CustomLogDataCollectionInfo) context.getDataCollectionInfo();
    savedDataCollectionInfo.setHosts(hostBatch);
    savedDataCollectionInfo.setStartMinute((int) collectionStartMinute);
    savedDataCollectionInfo.setCollectionTime(1);
    savedDataCollectionInfo.setEncryptedDataDetails(
        secretManager.getEncryptionDetails(datadogConfig, context.getAppId(), context.getWorkflowExecutionId()));
    return savedDataCollectionInfo;
  }

  private StackDriverLogDataCollectionInfo createStackDriverLogDataCollectionInfo(
      GcpConfig gcpConfig, AnalysisContext context, long collectionStartMinute, Set<String> hostBatch) {
    StackDriverLogDataCollectionInfo savedDataCollectionInfo =
        (StackDriverLogDataCollectionInfo) context.getDataCollectionInfo();
    savedDataCollectionInfo.setHosts(hostBatch);

    savedDataCollectionInfo.setStartMinute((int) collectionStartMinute);
    savedDataCollectionInfo.setStartTime(collectionStartMinute * TimeUnit.MINUTES.toMillis(1));
    savedDataCollectionInfo.setEndTime((collectionStartMinute + 1) * TimeUnit.MINUTES.toMillis(1));
    savedDataCollectionInfo.setCollectionTime(1);
    savedDataCollectionInfo.setEncryptedDataDetails(
        secretManager.getEncryptionDetails(gcpConfig, context.getAppId(), context.getWorkflowExecutionId()));
    return savedDataCollectionInfo;
  }

  private VerificationStateAnalysisExecutionData createLogAnalysisExecutionData(
      AnalysisContext context, Set<String> canaryNewHostNames, Map<String, String> lastExecutionNodes) {
    return VerificationStateAnalysisExecutionData.builder()
        .stateExecutionInstanceId(context.getStateExecutionId())
        .serverConfigId(context.getAnalysisServerConfigId())
        .query(context.getQuery())
        .timeDuration((int) TimeUnit.MINUTES.toMillis(1))
        .canaryNewHostNames(canaryNewHostNames)
        .lastExecutionNodes(lastExecutionNodes == null ? new HashSet<>() : new HashSet<>(lastExecutionNodes.keySet()))
        .correlationId(context.getCorrelationId())
        .build();
  }

  private DelegateTask createDelegateTask(
      TaskType taskType, String accountId, String appId, String waitId, Object[] dataCollectionInfo, String envId) {
    return DelegateTask.builder()
        .async(true)
        .accountId(accountId)
        .appId(appId)
        .waitId(waitId)
        .data(TaskData.builder()
                  .taskType(taskType.name())
                  .parameters(dataCollectionInfo)
                  .timeout(TimeUnit.MINUTES.toMillis(30))
                  .build())
        .envId(envId)
        .correlationId(waitId)
        .build();
  }

  private VerificationNodeDataSetupResponse getVerificationNodeDataResponse(
      String accountId, APMValidateCollectorConfig apmValidateCollectorConfig, String guid) {
    SyncTaskContext syncTaskContext =
        SyncTaskContext.builder().accountId(accountId).appId(GLOBAL_APP_ID).timeout(DEFAULT_SYNC_CALL_TIMEOUT).build();
    String apmResponse = delegateProxyFactory.get(APMDelegateService.class, syncTaskContext)
                             .fetch(apmValidateCollectorConfig, ThirdPartyApiCallLog.createApiCallLog(accountId, guid));
    JSONObject jsonObject = new JSONObject(apmResponse);

    boolean hasLoad = false;
    if (jsonObject.length() != 0) {
      hasLoad = true;
    }
    VerificationLoadResponse loadResponse =
        VerificationLoadResponse.builder().loadResponse(apmResponse).isLoadPresent(hasLoad).build();
    return VerificationNodeDataSetupResponse.builder()
        .providerReachable(hasLoad)
        .loadResponse(loadResponse)
        .dataForNode(apmResponse)
        .build();
  }

  @Override
  public boolean openAlert(String cvConfigId, ContinuousVerificationAlertData alertData) {
    final CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    Preconditions.checkNotNull(cvConfiguration, "No config found with id " + cvConfigId);
    Preconditions.checkNotNull(alertData, "Invalid alert data");
    alertData.setCvConfiguration(cvConfiguration);
    alertData.setPortalUrl(isNotEmpty(mainConfiguration.getApiUrl()) ? mainConfiguration.getApiUrl()
                                                                     : mainConfiguration.getPortal().getUrl());
    alertData.setAccountId(appService.getAccountIdByAppId(cvConfiguration.getAppId()));

    logger.info("Opening alert with riskscore {} for {}", alertData.getRiskScore(), cvConfiguration);
    alertService.openAlert(appService.getAccountIdByAppId(cvConfiguration.getAppId()), cvConfiguration.getAppId(),
        CONTINUOUS_VERIFICATION_ALERT, alertData);
    return true;
  }

  @Override
  public VerificationStateAnalysisExecutionData getVerificationStateExecutionData(String stateExecutionId) {
    final StateExecutionInstance stateExecutionInstance =
        wingsPersistence.get(StateExecutionInstance.class, stateExecutionId);
    if (stateExecutionInstance == null) {
      logger.info("No state execution found for {}", stateExecutionId);
      return null;
    }

    Map<String, StateExecutionData> stateExecutionMap = stateExecutionInstance.getStateExecutionMap();
    if (isEmpty(stateExecutionMap)) {
      logger.info("No state execution map found for {}", stateExecutionId);
      return null;
    }

    Preconditions.checkState(
        stateExecutionMap.size() == 1, "more than one entries in the stateExecutionMap for " + stateExecutionId);

    return (VerificationStateAnalysisExecutionData) stateExecutionMap.get(stateExecutionInstance.getDisplayName());
  }
}
