/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.artifactory.ArtifactoryClientImpl.getArtifactoryClient;
import static io.harness.artifactory.ArtifactoryClientImpl.getBaseUrl;
import static io.harness.artifactory.ArtifactoryClientImpl.handleAndRethrow;
import static io.harness.artifactory.ArtifactoryClientImpl.handleErrorResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ARTIFACT_SERVER_ERROR;
import static io.harness.eraro.ErrorCode.INVALID_ARTIFACT_SERVER;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.JSON;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.TEXT;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.GET;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.POST;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.docker;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.helm;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.maven;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.WingsException.ReportTarget;
import io.harness.network.Http;

import software.wings.beans.artifact.Artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.common.AlphanumComparator;
import software.wings.common.BuildDetailsComparatorAscending;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionTaskHelper;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHost;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.ProxyConfig;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.RepoPath;
import org.jfrog.artifactory.client.model.Repository;
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl;
import org.jfrog.artifactory.client.model.repository.settings.RepositorySettings;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
@BreakDependencyOn("software.wings.beans.artifact.ArtifactStreamAttributes")
@BreakDependencyOn("io.harness.delegate.task.ListNotifyResponseData")
public class ArtifactoryServiceImpl implements ArtifactoryService {
  private static final String REASON = "Reason:";
  private static final String CREATED_BY = "created_by";
  private static final String SYSTEM = "_system_";
  private static final String RESULTS = "results";
  private static final String KEY = "key";

  private static final String DOWNLOAD_FILE_FOR_GENERIC_REPO = "Downloading the file for generic repo";

  @Inject private ArtifactCollectionTaskHelper artifactCollectionTaskHelper;

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig) {
    return getRepositories(artifactoryConfig, Collections.singletonList(docker));
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType) {
    switch (packageType) {
      case "maven":
        return getRepositories(artifactoryConfig, Collections.singletonList(maven));
      default:
        return getRepositories(artifactoryConfig,
            Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
    }
  }

  @Override
  public Map<String, String> getRepositories(
      ArtifactoryConfigRequest artifactoryConfig, RepositoryType repositoryType) {
    switch (repositoryType) {
      case docker:
        return getRepositories(artifactoryConfig);
      case maven:
        return getRepositories(artifactoryConfig, Arrays.asList(maven));
      case any:
        return getRepositories(artifactoryConfig,
            Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
      default:
        return getRepositories(artifactoryConfig, "");
    }
  }

  private Map<String, String> getRepositories(
      ArtifactoryConfigRequest artifactoryConfig, List<PackageTypeImpl> packageTypes) {
    log.info("Retrieving repositories for packages {}", packageTypes.toArray());
    Map<String, String> repositories = new HashMap<>();
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    ArtifactoryRequest repositoryRequest =
        new ArtifactoryRequestImpl().apiUrl("api/repositories/").method(GET).responseType(JSON);
    String errorOccurredWhileRetrievingRepositories = "Error occurred while retrieving repositories";
    try {
      ArtifactoryResponse response = artifactory.restCall(repositoryRequest);
      handleErrorResponse(response);
      List<Map<Object, Object>> responseList = response.parseBody(List.class);
      for (Map<Object, Object> repository : responseList) {
        String repoKey = repository.get(KEY).toString();
        try {
          Repository repo = artifactory.repository(repoKey).get();
          RepositorySettings settings = repo.getRepositorySettings();
          if (packageTypes.contains(settings.getPackageType())) {
            repositories.put(repository.get(KEY).toString(), repository.get(KEY).toString());
          }
        } catch (Exception e) {
          log.warn("Failed to get repository settings for repo {}, Reason {}", repoKey, ExceptionUtils.getMessage(e));
          // TODO : Get Settings api only works for Artifactory Pro
          repositories.put(repository.get(KEY).toString(), repository.get(KEY).toString());
        }
      }
      if (repositories.isEmpty()) {
        // Better way of handling Unauthorized access
        log.info("Repositories are not available of package types {} or User not authorized to access artifactory",
            packageTypes);
      }
      log.info("Retrieving repositories for packages {} success", packageTypes.toArray());
    } catch (SocketTimeoutException e) {
      log.error(errorOccurredWhileRetrievingRepositories, e);
      return repositories;
    } catch (Exception e) {
      log.error(errorOccurredWhileRetrievingRepositories, e);
      handleAndRethrow(e, USER);
    }
    return repositories;
  }

  @Override
  public List<String> getRepoPaths(ArtifactoryConfigRequest artifactoryConfig, String repoKey) {
    return listDockerImages(getArtifactoryClient(artifactoryConfig), repoKey);
  }

  private List<String> listDockerImages(Artifactory artifactory, String repoKey) {
    List<String> images = new ArrayList<>();
    String errorOnListingDockerimages = "Error occurred while listing docker images from artifactory %s for Repo %s";
    try {
      log.info("Retrieving docker images from artifactory url {} and repo key {}", artifactory.getUri(), repoKey);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(new ArtifactoryRequestImpl()
                                                                         .apiUrl("api/docker/" + repoKey + "/v2"
                                                                             + "/_catalog")
                                                                         .method(GET)
                                                                         .responseType(JSON));
      handleErrorResponse(artifactoryResponse);
      Map response = artifactoryResponse.parseBody(Map.class);
      if (response != null) {
        images = (List<String>) response.get("repositories");
        if (isEmpty(images)) {
          log.info("No docker images from artifactory url {} and repo key {}", artifactory.getUri(), repoKey);
          images = new ArrayList<>();
        }
        log.info("Retrieving images from artifactory url {} and repo key {} success. Images {}", artifactory.getUri(),
            repoKey, images);
      }
    } catch (SocketTimeoutException e) {
      log.error(format(errorOnListingDockerimages, artifactory, repoKey), e);
      return images;
    } catch (Exception e) {
      log.error(format(errorOnListingDockerimages, artifactory, repoKey), e);
      handleAndRethrow(e, USER);
    }
    return images;
  }

  @Override
  public List<BuildDetails> getBuilds(ArtifactoryConfigRequest artifactoryConfig,
      ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds) {
    String repoKey = artifactStreamAttributes.getJobName();
    String imageName = artifactStreamAttributes.getImageName();
    log.info("Retrieving docker tags for repoKey {} imageName {} ", repoKey, imageName);
    List<BuildDetails> buildDetails = new ArrayList<>();
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    try {
      ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                                 .apiUrl("api/docker/" + repoKey + "/v2/" + imageName + "/tags/list")
                                                 .method(GET)
                                                 .responseType(JSON);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
      handleErrorResponse(artifactoryResponse);
      Map response = artifactoryResponse.parseBody(Map.class);
      if (response != null) {
        List<String> tags = (List<String>) response.get("tags");
        if (isEmpty(tags)) {
          log.info("No  docker tags for repoKey {} imageName {} success ", repoKey, imageName);
          return buildDetails;
        }
        String tagUrl = getBaseUrl(artifactoryConfig) + repoKey + "/" + imageName + "/";
        String repoName = ArtifactUtilities.getArtifactoryRepositoryName(artifactoryConfig.getArtifactoryUrl(),
            artifactStreamAttributes.getArtifactoryDockerRepositoryServer(), artifactStreamAttributes.getJobName(),
            artifactStreamAttributes.getImageName());
        buildDetails = tags.stream()
                           .map(tag -> {
                             Map<String, String> metadata = new HashMap();
                             metadata.put(ArtifactMetadataKeys.image, repoName + ":" + tag);
                             metadata.put(ArtifactMetadataKeys.tag, tag);
                             return aBuildDetails()
                                 .withNumber(tag)
                                 .withBuildUrl(tagUrl + tag)
                                 .withMetadata(metadata)
                                 .withUiDisplayName("Tag# " + tag)
                                 .build();
                           })
                           .collect(toList());
        if (tags.size() < 10) {
          log.info("Retrieving docker tags for repoKey {} imageName {} success. Retrieved tags {}", repoKey, imageName,
              tags);
        } else {
          log.info("Retrieving docker tags for repoKey {} imageName {} success. Retrieved {} tags", repoKey, imageName,
              tags.size());
        }
      }

    } catch (Exception e) {
      log.info("Exception occurred while retrieving the docker docker tags for Image {}", imageName);
      handleAndRethrow(e, USER);
    }

    // Sorting at build tag for docker artifacts.
    return buildDetails.stream().sorted(new BuildDetailsComparatorAscending()).collect(toList());
  }

  @Override
  public List<BuildDetails> getFilePaths(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactPath, String repositoryType, int maxVersions) {
    log.info("Retrieving file paths for repositoryName {} artifactPath {}", repositoryName, artifactPath);
    List<String> artifactPaths = new ArrayList<>();
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    String artifactName;
    try {
      String aclQuery = "api/search/aql";
      String requestBody;
      if (isNotBlank(artifactPath)) {
        if (artifactPath.charAt(0) == '/') {
          artifactPath = artifactPath.substring(1);
        }
        String subPath;
        if (repositoryType.equals("helm")) {
          requestBody = "items.find({\"repo\":\"" + repositoryName + "\"}, {\"@chart.name\": \"" + artifactPath
              + "\"}).include(\"@chart.version\").sort({\"$desc\" : [\"created\"]}).limit(" + maxVersions + ")";
        } else {
          if (artifactPath.contains("/")) {
            String[] pathElems = artifactPath.split("/");
            subPath = getPath(Arrays.stream(pathElems).limit(pathElems.length - 1).collect(toList()));
            artifactName = pathElems[pathElems.length - 1];
            if (!artifactName.contains("?") && !artifactName.contains("*")) {
              artifactName = artifactName + "*";
            }
            requestBody = "items.find({\"repo\":\"" + repositoryName + "\"}, {\"path\": {\"$match\":\"" + subPath
                + "\"}}, {\"name\": {\"$match\": \"" + artifactName + "\"}}).sort({\"$desc\" : [\"created\"]}).limit("
                + maxVersions + ")";
          } else {
            artifactPath = artifactPath + "*";
            requestBody = "items.find({\"repo\":\"" + repositoryName + "\"}, {\"depth\": 1}, {\"name\": {\"$match\": \""
                + artifactPath + "\"}}).sort({\"$desc\" : [\"created\"]}).limit(" + maxVersions + ")";
          }
        }
        ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                                   .apiUrl(aclQuery)
                                                   .method(POST)
                                                   .requestBody(requestBody)
                                                   .requestType(TEXT)
                                                   .responseType(JSON);
        ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
        if (artifactoryResponse.getStatusLine().getStatusCode() == 403
            || artifactoryResponse.getStatusLine().getStatusCode() == 400) {
          log.warn(
              "User not authorized to perform or using OSS version deep level search. Trying with different search api. Message {}",
              artifactoryResponse.getStatusLine().getReasonPhrase());
          return getBuildDetails(artifactoryConfig, artifactory, repositoryName, artifactPath, maxVersions);
        }
        Map<String, List> response = artifactoryResponse.parseBody(Map.class);
        if (response != null) {
          if (repositoryType.equals("helm")) {
            return getHelmChartDetailsFromResponse(response);
          }
          List<Map<String, String>> results = response.get(RESULTS);
          if (results != null) {
            for (Map<String, String> result : results) {
              String createdBy = result.get(CREATED_BY);
              if (createdBy == null || !createdBy.equals(SYSTEM)) {
                String path = result.get("path");
                String name = result.get("name");
                String size = String.valueOf(result.get("size"));
                if (path != null && !path.equals(".")) {
                  artifactPaths.add(repositoryName + "/" + path + "/" + name);
                  map.put(repositoryName + "/" + path + "/" + name, size);
                } else {
                  artifactPaths.add(repositoryName + "/" + name);
                  map.put(repositoryName + "/" + name, size);
                }
              }
            }
          }
        }
        log.info("Artifact paths order from Artifactory Server" + artifactPaths);
        Collections.reverse(artifactPaths);
        String finalArtifactPath = artifactPath;
        return artifactPaths.stream()
            .map(path
                -> aBuildDetails()
                       .withNumber(constructBuildNumber(finalArtifactPath, path.substring(path.indexOf('/') + 1)))
                       .withArtifactPath(path)
                       .withBuildUrl(getBaseUrl(artifactoryConfig) + path)
                       .withArtifactFileSize(map.get(path))
                       .withUiDisplayName(
                           "Build# " + constructBuildNumber(finalArtifactPath, path.substring(path.indexOf('/') + 1)))
                       .build())
            .collect(toList());
      } else {
        throw new ArtifactoryServerException("Artifact path can not be empty", INVALID_ARTIFACT_SERVER, USER);
      }
    } catch (Exception e) {
      log.error("Error occurred while retrieving File Paths from Artifactory server {}",
          artifactoryConfig.getArtifactoryUrl(), e);
      handleAndRethrow(e, USER);
    }
    return new ArrayList<>();
  }

  private List<BuildDetails> getHelmChartDetailsFromResponse(Map<String, List> response) {
    List<Map<String, Object>> results = response.get(RESULTS);
    List<BuildDetails> helmChartDetails = new ArrayList<>();
    if (results != null) {
      for (Map<String, Object> item : results) {
        String name = (String) item.get("name");
        List<Map<String, String>> properties = (List<Map<String, String>>) item.get("properties");
        if (isEmpty(properties)) {
          continue;
        }
        Map<String, String> versionProperty =
            properties.stream().filter(property -> property.get("key").equals("chart.version")).findAny().orElse(null);
        if (versionProperty == null) {
          continue;
        }
        String version = versionProperty.get("value");
        helmChartDetails.add(aBuildDetails().withNumber(version).withUiDisplayName(name).build());
      }
    }
    return helmChartDetails;
  }

  private String constructBuildNumber(String artifactPattern, String path) {
    String[] tokens = artifactPattern.split("/");
    for (String token : tokens) {
      if (token.contains("*") || token.contains("+")) {
        return path.substring(artifactPattern.indexOf(token));
      }
    }
    return path;
  }

  private List<String> getFilePathsForAnonymousUser(
      Artifactory artifactory, String repoKey, String artifactPath, int maxVersions) {
    log.info("Retrieving file paths for repoKey {} artifactPath {}", repoKey, artifactPath);
    List<String> artifactPaths = new ArrayList<>();

    List<FolderPath> folderPaths;
    try {
      if (isNotBlank(artifactPath)) {
        if (artifactPath.charAt(0) == '/') {
          artifactPath = artifactPath.substring(1);
        }
        if (artifactPath.endsWith("/")) {
          artifactPath = artifactPath.substring(0, artifactPath.length() - 1);
        }
        Pattern pattern = Pattern.compile(artifactPath.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
        if (artifactPath.contains("/")) {
          String[] pathElems = artifactPath.split("/");
          String subPath = getPath(Arrays.stream(pathElems).limit(pathElems.length - 1).collect(toList()));
          if (!subPath.contains("?") && !subPath.contains("*")) {
            folderPaths = getFolderPaths(artifactory, repoKey, "/" + subPath);
            if (folderPaths != null) {
              for (FolderPath folderPath : folderPaths) {
                if (!folderPath.isFolder()) {
                  if (pattern.matcher(folderPath.getPath().substring(1) + folderPath.getUri()).find()) {
                    artifactPaths.add(repoKey + folderPath.getPath() + folderPath.getUri());
                  }
                }
              }
            }
          } else {
            String artifactName = pathElems[pathElems.length - 1];
            List<RepoPath> repoPaths =
                artifactory.searches().artifactsByName(artifactName).repositories(repoKey).doSearch();
            if (repoPaths != null) {
              for (RepoPath repoPath : repoPaths) {
                if (pattern.matcher(repoPath.getItemPath()).find()) {
                  artifactPaths.add(repoKey + "/" + repoPath.getItemPath());
                }
              }
            }
          }
        } else {
          folderPaths = getFolderPaths(artifactory, repoKey, "");
          if (folderPaths != null) {
            for (FolderPath folderPath : folderPaths) {
              if (!folderPath.isFolder()) {
                if (pattern.matcher(folderPath.getUri()).find()) {
                  artifactPaths.add(repoKey + folderPath.getUri());
                }
              }
            }
          }
        }
        // Sort the alphanumeric order
        artifactPaths = artifactPaths.stream().sorted(new AlphanumComparator()).collect(toList());
        Collections.reverse(artifactPaths);
        artifactPaths = artifactPaths.stream().limit(maxVersions).collect(toList());
        Collections.reverse(artifactPaths);
      } else {
        throw new ArtifactoryServerException("Artifact path can not be empty", INVALID_ARTIFACT_SERVER);
      }
      log.info("Artifact paths order from Artifactory Server" + artifactPaths);
      return artifactPaths;
    } catch (Exception e) {
      log.error(
          format("Error occurred while retrieving File Paths from Artifactory server %s", artifactory.getUsername()),
          e);
      handleAndRethrow(e, USER);
    }
    return new ArrayList<>();
  }

  private String getPath(List<String> pathElems) {
    StringBuilder groupIdBuilder = new StringBuilder();
    for (int i = 0; i < pathElems.size(); i++) {
      groupIdBuilder.append(pathElems.get(i));
      if (i != pathElems.size() - 1) {
        groupIdBuilder.append('/');
      }
    }
    return groupIdBuilder.toString();
  }

  private List<FolderPath> getFolderPaths(Artifactory artifactory, String repoKey, String repoPath) {
    // Add first level paths
    List<FolderPath> folderPaths = new ArrayList<>();
    try {
      String apiStorageQuery = "api/storage/";
      apiStorageQuery = apiStorageQuery + repoKey + repoPath;
      ArtifactoryRequest repositoryRequest =
          new ArtifactoryRequestImpl().apiUrl(apiStorageQuery).method(GET).responseType(JSON);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
      handleErrorResponse(artifactoryResponse);
      LinkedHashMap<String, Object> response = artifactoryResponse.parseBody(LinkedHashMap.class);
      if (response == null) {
        return folderPaths;
      }
      List<LinkedHashMap<String, Object>> results = (List<LinkedHashMap<String, Object>>) response.get("children");
      if (isEmpty(results)) {
        return folderPaths;
      }
      for (LinkedHashMap<String, Object> result : results) {
        folderPaths.add(FolderPath.builder()
                            .path((String) response.get("path"))
                            .uri((String) result.get("uri"))
                            .folder((boolean) result.get("folder"))
                            .build());
      }
    } catch (Exception e) {
      log.error("Exception occurred in retrieving folder paths", e);
    }
    return folderPaths;
  }

  @Override
  public ListNotifyResponseData downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      Map<String, String> metadata, String delegateId, String taskId, String accountId) {
    ListNotifyResponseData res = new ListNotifyResponseData();
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath).replaceFirst(repositoryName, "").substring(1);
    String artifactName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    try {
      log.info(DOWNLOAD_FILE_FOR_GENERIC_REPO);
      InputStream inputStream = downloadArtifacts(artifactoryConfig, repositoryName, metadata);
      artifactCollectionTaskHelper.addDataToResponse(
          new ImmutablePair<>(artifactName, inputStream), artifactPath, res, delegateId, taskId, accountId);
      return res;
    } catch (Exception e) {
      String msg =
          "Failed to download the latest artifacts  of repo [" + repositoryName + "] artifactPath [" + artifactPath;
      prepareAndThrowException(msg + REASON + ExceptionUtils.getRootCauseMessage(e), USER, e);
    }
    return res;
  }

  @Override
  public Pair<String, InputStream> downloadArtifact(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, Map<String, String> metadata) {
    Pair<String, InputStream> pair = null;
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath).replaceFirst(repositoryName, "").substring(1);
    String artifactName = metadata.get(ArtifactMetadataKeys.artifactFileName);
    try {
      log.info(DOWNLOAD_FILE_FOR_GENERIC_REPO);
      InputStream inputStream = downloadArtifacts(artifactoryConfig, repositoryName, metadata);
      pair = new ImmutablePair<>(artifactName, inputStream);
    } catch (Exception e) {
      String msg =
          "Failed to download the latest artifacts  of repo [" + repositoryName + "] artifactPath [" + artifactPath;
      prepareAndThrowException(msg + REASON + ExceptionUtils.getRootCauseMessage(e), USER, e);
    }
    return pair;
  }

  @Override
  public boolean validateArtifactPath(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, String repositoryType) {
    log.info("Validating artifact path {} for repository {} and repositoryType {}", artifactPath, repositoryName,
        repositoryType);
    if (isBlank(artifactPath)) {
      throw new ArtifactoryServerException("Artifact Pattern can not be empty", ARTIFACT_SERVER_ERROR, USER);
    }
    List<BuildDetails> filePaths = getFilePaths(artifactoryConfig, repositoryName, artifactPath, repositoryType, 1);

    if (isEmpty(filePaths)) {
      prepareAndThrowException("No artifact files matching with the artifact path [" + artifactPath + "]", USER, null);
    }
    log.info("Validating whether directory exists or not for Generic repository type by fetching file paths");
    return true;
  }

  private void prepareAndThrowException(String message, EnumSet<ReportTarget> reportTargets, Exception e) {
    throw new ArtifactoryServerException(message, ErrorCode.INVALID_ARTIFACT_SERVER, reportTargets, e);
  }

  private InputStream downloadArtifacts(
      ArtifactoryConfigRequest artifactoryConfig, String repoKey, Map<String, String> metadata) {
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    Set<String> artifactNames = new HashSet<>();
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath).replaceFirst(repoKey, "").substring(1);
    String artifactName = metadata.get(ArtifactMetadataKeys.artifactFileName);

    try {
      log.info("Artifact name {}", artifactName);
      if (artifactNames.add(artifactName)) {
        if (metadata.get(ArtifactMetadataKeys.artifactPath) != null) {
          log.info(DOWNLOAD_FILE_FOR_GENERIC_REPO);
          log.info("Downloading file {} ", artifactPath);
          InputStream inputStream = artifactory.repository(repoKey).download(artifactPath).doDownload();
          log.info("Downloading file {} success", artifactPath);
          return inputStream;
        }
      }
    } catch (Exception e) {
      log.error("Failed to download the artifact of repository {} from path {}", repoKey, artifactPath, e);
      String msg =
          "Failed to download the latest artifacts  of repository [" + repoKey + "] file path [" + artifactPath;
      throw new ArtifactoryServerException(
          msg + REASON + ExceptionUtils.getRootCauseMessage(e), ARTIFACT_SERVER_ERROR, USER);
    }
    log.info(
        "Downloading artifacts from artifactory for repository  {} and file path {} success", repoKey, artifactPath);
    return null;
  }

  protected void checkIfUseProxyAndAppendConfig(
      ArtifactoryClientBuilder builder, ArtifactoryConfigRequest artifactoryConfig) {
    HttpHost httpProxyHost = Http.getHttpProxyHost(artifactoryConfig.getArtifactoryUrl());
    if (httpProxyHost != null && !Http.shouldUseNonProxy(artifactoryConfig.getArtifactoryUrl())) {
      builder.setProxy(new ProxyConfig(httpProxyHost.getHostName(), httpProxyHost.getPort(), Http.getProxyScheme(),
          Http.getProxyUserName(), Http.getProxyPassword()));
    }
  }

  @Override
  public Long getFileSize(ArtifactoryConfigRequest artifactoryConfig, Map<String, String> metadata) {
    String artifactPath = metadata.get(ArtifactMetadataKeys.artifactPath);
    log.info("Retrieving file paths for artifactPath {}", artifactPath);
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    try {
      String apiStorageQuery = "api/storage/" + artifactPath;

      ArtifactoryRequest repositoryRequest =
          new ArtifactoryRequestImpl().apiUrl(apiStorageQuery).method(GET).requestType(TEXT).responseType(JSON);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
      handleErrorResponse(artifactoryResponse);
      LinkedHashMap<String, String> response = artifactoryResponse.parseBody(LinkedHashMap.class);
      if (response != null && isNotBlank(response.get("size"))) {
        return Long.valueOf(response.get("size"));
      } else {
        throw new ArtifactoryServerException(
            "Unable to get artifact file size. The file probably does not exist", INVALID_ARTIFACT_SERVER, USER);
      }
    } catch (Exception e) {
      log.error("Error occurred while retrieving File Paths from Artifactory server {}",
          artifactoryConfig.getArtifactoryUrl(), e);
      handleAndRethrow(e, USER);
    }
    return 0L;
  }

  @Override
  public void checkIfValidHelmRepository(ArtifactoryConfigRequest artifactoryConfigRequest, String repoName) {
    log.info("Checking if valid helm repo for {}", repoName);
    Artifactory artifactory = getArtifactoryClient(artifactoryConfigRequest);
    String errorOccurredWhileRetrievingRepositories = "Error occurred while validating repository";
    try {
      Repository repo = artifactory.repository(repoName).get();
      RepositorySettings settings = repo.getRepositorySettings();
      if (settings.getPackageType() != helm) {
        throw new InvalidArtifactServerException("Given chart url is not a valid helm repo");
      }
    } catch (Exception e) {
      log.error(errorOccurredWhileRetrievingRepositories, e);
      handleAndRethrow(e, USER);
    }
  }

  private List<BuildDetails> getBuildDetails(ArtifactoryConfigRequest artifactoryConfig, Artifactory artifactory,
      String repositoryName, String artifactPath, int maxVersions) {
    List<String> artifactPaths = getFilePathsForAnonymousUser(artifactory, repositoryName, artifactPath, maxVersions);
    return artifactPaths.stream()
        .map(path
            -> aBuildDetails()
                   .withNumber(constructBuildNumber(artifactPath, path.substring(path.indexOf('/') + 1)))
                   .withArtifactPath(path)
                   .withBuildUrl(getBaseUrl(artifactoryConfig) + path)
                   .withUiDisplayName(
                       "Build# " + constructBuildNumber(artifactPath, path.substring(path.indexOf('/') + 1)))
                   .build())
        .collect(toList());
  }
}
