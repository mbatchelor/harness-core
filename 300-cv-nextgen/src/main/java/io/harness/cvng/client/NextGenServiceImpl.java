package io.harness.cvng.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hazelcast.util.Preconditions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CV)
public class NextGenServiceImpl implements NextGenService {
  @Inject private NextGenClient nextGenClient;
  @Inject private RequestExecutor requestExecutor;

  private LoadingCache<EntityKey, EnvironmentResponseDTO> environmentCache =
      CacheBuilder.newBuilder()
          .maximumSize(100000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, EnvironmentResponseDTO>() {
            @Override
            public EnvironmentResponseDTO load(EntityKey entityKey) {
              EnvironmentResponse environmentResponse =
                  requestExecutor
                      .execute(nextGenClient.getEnvironment(entityKey.getEntityIdentifier(), entityKey.getAccountId(),
                          entityKey.getOrgIdentifier(), entityKey.getProjectIdentifier()))
                      .getData();
              Preconditions.checkNotNull(environmentResponse, "Environment Response from Ng Manager cannot be null");
              return environmentResponse.getEnvironment();
            }
          });

  private LoadingCache<EntityKey, ServiceResponseDTO> serviceCache =
      CacheBuilder.newBuilder()
          .maximumSize(100000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, ServiceResponseDTO>() {
            @Override
            public ServiceResponseDTO load(EntityKey entityKey) {
              return requestExecutor
                  .execute(nextGenClient.getService(entityKey.getEntityIdentifier(), entityKey.getAccountId(),
                      entityKey.getOrgIdentifier(), entityKey.getProjectIdentifier()))
                  .getData();
            }
          });

  private LoadingCache<EntityKey, ProjectDTO> projectCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, ProjectDTO>() {
            @Override
            public ProjectDTO load(EntityKey entityKey) {
              return requestExecutor
                  .execute(nextGenClient.getProject(
                      entityKey.getProjectIdentifier(), entityKey.getAccountId(), entityKey.getOrgIdentifier()))
                  .getData()
                  .getProject();
            }
          });

  private LoadingCache<EntityKey, OrganizationDTO> orgCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(4, TimeUnit.HOURS)
          .build(new CacheLoader<EntityKey, OrganizationDTO>() {
            @Override
            public OrganizationDTO load(EntityKey entityKey) {
              return requestExecutor
                  .execute(nextGenClient.getOrganization(entityKey.getOrgIdentifier(), entityKey.getAccountId()))
                  .getData()
                  .getOrganization();
            }
          });

  @Override
  public ConnectorResponseDTO create(ConnectorDTO connectorRequestDTO, String accountIdentifier) {
    return requestExecutor.execute(nextGenClient.create(connectorRequestDTO, accountIdentifier)).getData();
  }

  @Override
  public Optional<ConnectorInfoDTO> get(
      String accountIdentifier, String connectorIdentifier, String orgIdentifier, String projectIdentifier) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    ConnectorResponseDTO connectorResponse =
        requestExecutor
            .execute(nextGenClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
                identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()))
            .getData();
    return connectorResponse != null ? Optional.of(connectorResponse.getConnector()) : Optional.empty();
  }

  @Override
  public EnvironmentResponseDTO getEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    try {
      return environmentCache.get(EntityKey.builder()
                                      .accountId(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .entityIdentifier(environmentIdentifier)
                                      .build());
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public ServiceResponseDTO getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    try {
      return serviceCache.get(EntityKey.builder()
                                  .accountId(accountId)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .entityIdentifier(serviceIdentifier)
                                  .build());
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public List<EnvironmentResponse> listEnvironment(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> environmentIdentifiers) {
    PageResponse<EnvironmentResponse> environmentsResponse =
        requestExecutor
            .execute(nextGenClient.listEnvironment(accountId, orgIdentifier, projectIdentifier, environmentIdentifiers))
            .getData();

    return environmentsResponse.getContent();
  }

  @Override
  public List<ServiceResponse> listService(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    PageResponse<ServiceResponse> servicesResponse =
        requestExecutor
            .execute(nextGenClient.listService(accountId, orgIdentifier, projectIdentifier, serviceIdentifiers))
            .getData();

    return servicesResponse.getContent();
  }

  @Override
  public ProjectDTO getProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getProject(accountIdentifier, orgIdentifier, projectIdentifier, false);
  }

  @Override
  public ProjectDTO getCachedProject(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getProject(accountIdentifier, orgIdentifier, projectIdentifier, true);
  }

  private ProjectDTO getProject(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, boolean isCached) {
    if (isCached) {
      try {
        return projectCache.get(EntityKey.builder()
                                    .accountId(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build());
      } catch (ExecutionException ex) {
        throw new RuntimeException(ex);
      }
    }

    return requestExecutor.execute(nextGenClient.getProject(projectIdentifier, accountIdentifier, orgIdentifier))
        .getData()
        .getProject();
  }

  @Override
  public OrganizationDTO getOrganization(String accountIdentifier, String orgIdentifier) {
    try {
      return orgCache.get(EntityKey.builder().accountId(accountIdentifier).orgIdentifier(orgIdentifier).build());
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public int getServicesCount(String accountId, String orgIdentifier, String projectIdentifier) {
    return (int) requestExecutor
        .execute(nextGenClient.listServicesForProject(0, 1000, accountId, orgIdentifier, projectIdentifier, null))
        .getData()
        .getTotalItems();
  }

  @Override
  public int getEnvironmentCount(String accountId, String orgIdentifier, String projectIdentifier) {
    return (int) requestExecutor
        .execute(
            nextGenClient.listEnvironmentsForProject(0, 1000, accountId, orgIdentifier, projectIdentifier, null, null))
        .getData()
        .getTotalItems();
  }

  @Value
  @Builder
  public static class EntityKey {
    private String accountId;
    private String orgIdentifier;
    private String projectIdentifier;
    private String entityIdentifier;
  }
}
