package software.wings.helpers.ext.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.PcfRequestConfig;

import software.wings.beans.command.ExecutionLogCallback;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationEnvironments;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
import org.cloudfoundry.operations.routes.Route;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public interface PcfClient {
  /**
   * Get organizations.
   *
   * @param pcfRequestConfig PcfRequestConfig
   * @return List<OrganizationSummary>
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<OrganizationSummary> getOrganizations(PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get space for organization.
   *
   * @param pcfRequestConfig PcfRequestConfig
   * @return List<String>
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<String> getSpacesForOrganization(PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get applications.
   *
   * @param pcfRequestConfig PcfRequestConfig
   * @return List<ApplicationSummary>
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  List<ApplicationSummary> getApplications(PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Get application by name.
   *
   * @param pcfRequestConfig PcfRequestConfig
   * @return ApplicationDetail
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  ApplicationDetail getApplicationByName(PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;

  /**
   * Start applications.
   *
   * @param pcfRequestConfig
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void startApplication(PcfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Scale application.
   *
   * @param pcfRequestConfig
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void scaleApplications(PcfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Stop application.
   *
   * @param pcfRequestConfig
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void stopApplication(PcfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  /**
   * Delete application.
   *
   * @param pcfRequestConfig
   * @throws PivotalClientApiException
   * @throws InterruptedException
   */
  void deleteApplication(PcfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;

  void pushUsingPcfSdk(PcfRequestConfig pcfRequestConfig, Path path, ExecutionLogCallback executionLogCallback)
      throws PivotalClientApiException, InterruptedException;

  void createRouteMap(PcfRequestConfig pcfRequestConfig, String host, String domain, String path, boolean tcpRoute,
      boolean useRandomPort, Integer port) throws PivotalClientApiException, InterruptedException;
  void unmapRouteMapForApp(PcfRequestConfig pcfRequestConfig, Route route)
      throws PivotalClientApiException, InterruptedException;
  void mapRoutesForApplication(PcfRequestConfig pcfRequestConfig, List<String> routes)
      throws PivotalClientApiException, InterruptedException;
  void mapRouteMapForApp(PcfRequestConfig pcfRequestConfig, Route route)
      throws PivotalClientApiException, InterruptedException;
  List<Route> getRouteMapsByNames(List<String> paths, PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;
  void getTasks(PcfRequestConfig pcfRequestConfig) throws PivotalClientApiException, InterruptedException;
  List<String> getRoutesForSpace(PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException, InterruptedException;
  Optional<Route> getRouteMap(PcfRequestConfig pcfRequestConfig, String route)
      throws PivotalClientApiException, InterruptedException;
  void unmapRoutesForApplication(PcfRequestConfig pcfRequestConfig, List<String> routes)
      throws PivotalClientApiException, InterruptedException;
  List<LogMessage> getRecentLogs(PcfRequestConfig pcfRequestConfig, long logsAfterTsNs)
      throws PivotalClientApiException;
  ApplicationEnvironments getApplicationEnvironmentsByName(PcfRequestConfig pcfRequestConfig)
      throws PivotalClientApiException;
}
