package io.harness.delegate.task.helm;

import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.LogCallback;

import java.util.List;
import lombok.Builder;

public class HelmReleaseHistoryCommandRequestNG extends HelmCommandRequestNG {
  @Builder
  public HelmReleaseHistoryCommandRequestNG(boolean skipDryRun, String releaseName, List<String> valuesYamlList,
      K8sInfraDelegateConfig k8sInfraDelegateConfig, ManifestDelegateConfig manifestDelegateConfig, String accountId,
      boolean skipSteadyStateCheck, boolean shouldOpenFetchFilesLogStream, CommandUnitsProgress commandUnitsProgress,
      LogCallback logCallback, String namespace, HelmVersion helmVersion, String commandFlags, String repoName,
      String workingDir, String kubeConfigLocation, String ocPath, String commandName,
      boolean useLatestKubectlVersion) {
    super(skipDryRun, releaseName, HelmCommandType.RELEASE_HISTORY, valuesYamlList, k8sInfraDelegateConfig,
        manifestDelegateConfig, accountId, skipSteadyStateCheck, shouldOpenFetchFilesLogStream, commandUnitsProgress,
        logCallback, namespace, helmVersion, commandFlags, repoName, workingDir, kubeConfigLocation, ocPath,
        commandName, useLatestKubectlVersion);
  }
}