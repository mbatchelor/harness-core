package software.wings.beans;

import lombok.Getter;
import software.wings.beans.FeatureFlag.Scope;

/**
 * Add your feature name here. When the feature is fully launched and no longer needs to be flagged,
 * delete the feature name.
 */
public enum FeatureName {
  ACTIVE_MIGRATION_FROM_AWS_TO_GCP_KMS,
  APPD_CV_TASK,
  ARTIFACT_PERPETUAL_TASK,
  ARTIFACT_PERPETUAL_TASK_MIGRATION,
  ARTIFACT_STREAM_REFACTOR,
  AZURE_US_GOV_CLOUD,
  AZURE_VMSS,
  BIND_FETCH_FILES_TASK_TO_DELEGATE,
  CONNECTORS_REF_SECRETS,
  CONNECTORS_REF_SECRETS_MIGRATION,
  CUSTOM_APM_24_X_7_CV_TASK,
  CUSTOM_APM_CV_TASK,
  CUSTOM_DASHBOARD,
  CUSTOM_DEPLOYMENT,
  CUSTOM_SECRETS_MANAGER,
  CV_DATA_COLLECTION_JOB,
  CV_DEMO,
  CV_EXPOSE_INSTANCE_DETAILS,
  CV_FEEDBACKS,
  CV_HOST_SAMPLING,
  CV_SUCCEED_FOR_ANOMALY,
  DEFAULT_ARTIFACT,
  DELEGATE_CAPABILITY_FRAMEWORK_PHASE_ENABLE,
  DELEGATE_SCALING_GROUP,
  DELEGATE_SCOPE_REVAMP,
  DELEGATE_SCOPE_TAG_SELECTORS,
  DEPLOY_TO_SPECIFIC_HOSTS,
  DISABLE_ADDING_SERVICE_VARS_TO_ECS_SPEC,
  DISABLE_DELEGATE_SELECTION_LOG,
  DISABLE_LOGML_NEURAL_NET,
  DISABLE_METRIC_NAME_CURLY_BRACE_CHECK,
  DISABLE_SERVICEGUARD_LOG_ALERTS,
  DISABLE_WINRM_COMMAND_ENCODING,
  ENTITY_AUDIT_RECORD,
  ECS_REMOTE_MANIFEST,
  GCB_CI_SYSTEM,
  GIT_ACCOUNT_SUPPORT,
  GIT_HTTPS_KERBEROS,
  GLOBAL_COMMAND_LIBRARY,
  GLOBAL_CV_DASH,
  GLOBAL_DISABLE_HEALTH_CHECK(Scope.GLOBAL),
  GRAPHQL_DEV,
  HARNESS_TAGS,
  HELM_STEADY_STATE_CHECK_1_16,
  INFRA_MAPPING_REFACTOR,
  INLINE_SSH_COMMAND,
  LIMIT_PCF_THREADS,
  LOCAL_DELEGATE_CONFIG_OVERRIDE,
  LOGS_V2_247,
  MOVE_AWS_AMI_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_AMI_SPOT_INST_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_CODE_DEPLOY_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_LAMBDA_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_AWS_SSH_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_CONTAINER_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MOVE_PCF_INSTANCE_SYNC_TO_PERPETUAL_TASK,
  MULTISELECT_INFRA_PIPELINE,
  NAS_SUPPORT,
  NEW_INSTANCE_TIMESERIES,
  NEW_RELIC_CV_TASK,
  NEWRELIC_24_7_CV_TASK,
  ON_NEW_ARTIFACT_TRIGGER_WITH_LAST_COLLECTED_FILTER,
  OUTAGE_CV_DISABLE,
  PIPELINE_GOVERNANCE,
  REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH,
  REVALIDATE_WHITELISTED_DELEGATE,
  SCIM_INTEGRATION,
  SEARCH(Scope.GLOBAL),
  SEARCH_REQUEST,
  SEND_LOG_ANALYSIS_COMPRESSED,
  SEND_SLACK_NOTIFICATION_FROM_DELEGATE,
  SIDE_NAVIGATION,
  SLACK_APPROVALS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_AMI_SPOT_INST_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_CODE_DEPLOY_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_LAMBDA_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_SSH_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AZURE_INFRA_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_CONTAINER_DEPLOYMENTS,
  STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_PCF_DEPLOYMENTS,
  SUPERVISED_TS_THRESHOLD,
  SWITCH_GLOBAL_TO_GCP_KMS,
  TEMPLATIZED_SECRET_MANAGER,
  THREE_PHASE_SECRET_DECRYPTION,
  TIME_RANGE_FREEZE_GOVERNANCE,
  TRIGGER_FOR_ALL_ARTIFACTS,
  TRIGGER_YAML,
  UI_ALLOW_K8S_V1,
  USE_CDN_FOR_STORAGE_FILES,
  USE_NEXUS3_PRIVATE_APIS,
  WEEKLY_WINDOW,
  YAML_RBAC,
  ROLLBACK_NONE_ARTIFACT,
  EXPORT_TF_PLAN,
  ;

  FeatureName() {
    scope = Scope.PER_ACCOUNT;
  }

  FeatureName(Scope scope) {
    this.scope = scope;
  }

  @Getter private FeatureFlag.Scope scope;
}
