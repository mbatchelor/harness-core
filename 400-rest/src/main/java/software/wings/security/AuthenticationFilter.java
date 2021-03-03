package software.wings.security;

import static io.harness.AuthorizationServiceHeader.DEFAULT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.eraro.ErrorCode.USER_DOES_NOT_EXIST;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.security.JWTAuthenticationFilter.setSourcePrincipalInContext;
import static io.harness.security.JWTTokenServiceUtils.extractToken;
import static io.harness.security.JWTTokenServiceUtils.verifyJWTToken;

import static java.util.Collections.emptyMap;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.apache.commons.lang3.StringUtils.substringAfter;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.logging.AccountLogContext;
import io.harness.manage.GlobalContextManager;
import io.harness.security.JWTAuthenticationFilter;
import io.harness.security.JWTTokenHandler;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.LearningEngineAuth;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.annotations.PublicApi;
import io.harness.security.annotations.PublicApiWithWhitelist;

import software.wings.beans.AuthToken;
import software.wings.beans.User;
import software.wings.common.AuditHelper;
import software.wings.security.annotations.AdminPortalAuth;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.ExternalFacingApiAuth;
import software.wings.security.annotations.IdentityServiceAuth;
import software.wings.security.annotations.ScimAPI;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.ExternalApiRateLimitingService;
import software.wings.service.intfc.HarnessApiKeyService;
import software.wings.service.intfc.UserService;

import com.auth0.jwt.interfaces.Claim;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Priority;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {
  @VisibleForTesting public static final String API_KEY_HEADER = "X-Api-Key";
  @VisibleForTesting public static final String HARNESS_API_KEY_HEADER = "X-Harness-Api-Key";
  @VisibleForTesting public static final String USER_IDENTITY_HEADER = "X-Identity-User";
  public static final String IDENTITY_SERVICE_PREFIX = "IdentityService ";
  public static final String ADMIN_PORTAL_PREFIX = "AdminPortal ";
  public static final String NEXT_GEN_MANAGER_PREFIX = "NextGenManager ";
  private static final int NUM_MANAGERS = 3;

  @Context private ResourceInfo resourceInfo;
  private AuthService authService;
  private UserService userService;
  private AuditService auditService;
  private ApiKeyService apiKeyService;
  private HarnessApiKeyService harnessApiKeyService;
  private AuditHelper auditHelper;
  private ExternalApiRateLimitingService rateLimitingService;
  private SecretManager secretManager;
  private Map<String, String> serviceToSecretMapping;
  private Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping;

  @Inject
  public AuthenticationFilter(UserService userService, AuthService authService, AuditService auditService,
      AuditHelper auditHelper, ApiKeyService apiKeyService, HarnessApiKeyService harnessApiKeyService,
      ExternalApiRateLimitingService rateLimitingService, SecretManager secretManager) {
    this.userService = userService;
    this.authService = authService;
    this.auditService = auditService;
    this.auditHelper = auditHelper;
    this.apiKeyService = apiKeyService;
    this.harnessApiKeyService = harnessApiKeyService;
    this.rateLimitingService = rateLimitingService;
    this.secretManager = secretManager;
    serviceToSecretMapping = getServiceToSecretMapping();
    serviceToJWTTokenHandlerMapping = getServiceToJWTTokenHandlerMapping();
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    if (authenticationExemptedRequests(containerRequestContext)) {
      return;
    }

    if (delegateAPI()) {
      validateDelegateRequest(containerRequestContext);
      return;
    }

    if (learningEngineServiceAPI()) {
      validateLearningEngineRequest(containerRequestContext);
      return; // do nothing
    }

    if (isScimAPI()) {
      return;
    }

    String authorization = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    if (isExternalFacingApiRequest(containerRequestContext) || isApiKeyAuthorizationAPI()) {
      String apiKey = containerRequestContext.getHeaderString(API_KEY_HEADER);

      if (isNotEmpty(apiKey)) {
        if (!containerRequestContext.getUriInfo().getAbsolutePath().getPath().endsWith("graphql")) {
          ensureValidQPM(containerRequestContext.getHeaderString(API_KEY_HEADER));
        }

        try {
          validateExternalFacingApiRequest(containerRequestContext);
          return;
        } catch (UnauthorizedException | InvalidRequestException exception) {
          if (authorization == null) {
            throw exception;
          }
        }
      }

      if (checkIfBearerTokenAndValidate(authorization, containerRequestContext)) {
        return;
      }
    }

    if (harnessApiKeyService.isHarnessClientApi(resourceInfo)) {
      harnessApiKeyService.validateHarnessClientApiRequest(resourceInfo, containerRequestContext);
      return;
    }

    if (authorization == null) {
      throw new InvalidRequestException(INVALID_TOKEN.name(), INVALID_TOKEN, USER);
    }

    GlobalContextManager.set(new GlobalContext());

    if (isAdminPortalRequest()) {
      String adminPortalToken =
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), ADMIN_PORTAL_PREFIX)
              .trim();
      secretManager.verifyJWTToken(adminPortalToken, JWT_CATEGORY.DATA_HANDLER_SECRET);
      return;
    }

    if (isNextGenManagerRequest(resourceInfo)) {
      validateNextGenRequest(containerRequestContext);
      return;
    }

    if (isIdentityServiceOriginatedRequest(containerRequestContext)) {
      String identityServiceToken =
          substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), IDENTITY_SERVICE_PREFIX);
      Map<String, Claim> claimMap =
          secretManager.verifyJWTToken(identityServiceToken, JWT_CATEGORY.IDENTITY_SERVICE_SECRET);
      HarnessUserAccountActions harnessUserAccountActions = secretManager.getHarnessUserAccountActions(claimMap);
      HarnessUserThreadLocal.set(harnessUserAccountActions);
      if (isAuthenticatedByIdentitySvc(containerRequestContext)) {
        SecurityContextBuilder.setContext(claimMap);

        setSourcePrincipalInContext(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping,
            SecurityContextBuilder.getPrincipal());

        String userId = containerRequestContext.getHeaderString(USER_IDENTITY_HEADER);
        User user = userService.getUserFromCacheOrDB(userId);
        if (user != null) {
          UserThreadLocal.set(user);
          return;
        } else {
          throw new InvalidRequestException(USER_DOES_NOT_EXIST.name(), USER_DOES_NOT_EXIST, USER);
        }
      } else if (identityServiceAPI()) {
        return;
      }
      throw new InvalidRequestException(INVALID_CREDENTIAL.name(), INVALID_CREDENTIAL, USER);
    }

    // Bearer token validation is needed for environments without Gateway
    if (checkIfBearerTokenAndValidate(authorization, containerRequestContext)) {
      setSourcePrincipalInContext(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping,
          SecurityContextBuilder.getPrincipal());
      return;
    }

    throw new InvalidRequestException(INVALID_CREDENTIAL.name(), INVALID_CREDENTIAL, USER);
  }

  protected boolean isScimAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(ScimAPI.class) != null || resourceClass.getAnnotation(ScimAPI.class) != null;
  }

  protected boolean isAuthenticatedByIdentitySvc(ContainerRequestContext containerRequestContext) {
    String value = containerRequestContext.getHeaderString(USER_IDENTITY_HEADER);
    return isNotEmpty(value);
  }

  protected boolean isAdminPortalRequest() {
    return resourceInfo.getResourceMethod().getAnnotation(AdminPortalAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(AdminPortalAuth.class) != null;
  }

  @VisibleForTesting
  boolean isNextGenManagerRequest(ResourceInfo requestResourceInfo) {
    return requestResourceInfo.getResourceMethod().getAnnotation(NextGenManagerAuth.class) != null
        || requestResourceInfo.getResourceClass().getAnnotation(NextGenManagerAuth.class) != null;
  }

  @VisibleForTesting
  boolean isNextGenAuthorizationValid(ContainerRequestContext containerRequestContext) {
    String nextGenManagerToken =
        substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), NEXT_GEN_MANAGER_PREFIX)
            .trim();
    secretManager.verifyJWTToken(nextGenManagerToken, JWT_CATEGORY.NEXT_GEN_MANAGER_SECRET);
    return true;
  }

  private boolean checkIfBearerTokenAndValidate(String authHeader, ContainerRequestContext containerRequestContext) {
    if (authHeader != null && authHeader.startsWith("Bearer")) {
      User user = validateBearerToken(containerRequestContext);
      containerRequestContext.setProperty("USER", user);
      updateUserInAuditRecord(user);
      UserThreadLocal.set(user);
      setPrincipal(extractToken(containerRequestContext, "Bearer"));
      return true;
    }
    return false;
  }

  private void setPrincipal(String tokenString) {
    if (tokenString.length() > 32) {
      Map<String, Claim> claimMap = verifyJWTToken(tokenString, secretManager.getJWTSecret(JWT_CATEGORY.AUTH_SECRET));
      SecurityContextBuilder.setContext(claimMap);
    }
  }

  private Map<String, String> getServiceToSecretMapping() {
    Map<String, String> mapping = new HashMap<>();
    mapping.put(DEFAULT.getServiceId(), secretManager.getJWTSecret(JWT_CATEGORY.NEXT_GEN_MANAGER_SECRET));
    return mapping;
  }

  private Map<String, JWTTokenHandler> getServiceToJWTTokenHandlerMapping() {
    return emptyMap();
  }

  private void validateNextGenRequest(ContainerRequestContext containerRequestContext) {
    JWTAuthenticationFilter.filter(containerRequestContext, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
  }

  private void ensureValidQPM(String key) {
    if (rateLimitingService.rateLimitRequest(key)) {
      throw new WebApplicationException(Response.status(429)
                                            .entity("Too Many requests. Throttled. Max QPS: "
                                                + rateLimitingService.getMaxQPMPerManager() * NUM_MANAGERS / 60)
                                            .build());
    }
  }

  private User validateBearerToken(ContainerRequestContext containerRequestContext) {
    String tokenString = extractToken(containerRequestContext, "Bearer");
    AuthToken authToken = authService.validateToken(tokenString);
    User user = authToken.getUser();
    if (user != null) {
      user.setToken(tokenString);
      return user;
    }
    throw new InvalidRequestException(INVALID_TOKEN.name(), INVALID_TOKEN, USER);
  }

  private void updateUserInAuditRecord(User user) {
    auditService.updateUser(auditHelper.get(), user.getPublicUser());
  }

  protected void validateLearningEngineRequest(ContainerRequestContext containerRequestContext) {
    String header = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

    if (isEmpty(header)) {
      throw new IllegalStateException("Invalid verification header");
    }

    authService.validateLearningEngineServiceToken(substringAfter(header, "LearningEngine "));
  }

  protected void validateDelegateRequest(ContainerRequestContext containerRequestContext) {
    MultivaluedMap<String, String> pathParameters = containerRequestContext.getUriInfo().getPathParameters();
    MultivaluedMap<String, String> queryParameters = containerRequestContext.getUriInfo().getQueryParameters();

    String accountId = getRequestParamFromContext("accountId", pathParameters, queryParameters);
    try (AccountLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String header = containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
      if (header != null && header.contains("Delegate")) {
        authService.validateDelegateToken(
            accountId, substringAfter(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate "));
      } else {
        throw new IllegalStateException("Invalid header:" + header);
      }
    }
  }

  protected void validateExternalFacingApiRequest(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(API_KEY_HEADER);
    if (isBlank(apiKey)) {
      throw new InvalidRequestException("Api Key not supplied", USER);
    }
    String accountId = getRequestParamFromContext("accountId", containerRequestContext.getUriInfo().getPathParameters(),
        containerRequestContext.getUriInfo().getQueryParameters());

    if (isEmpty(accountId)) {
      // In case of graphql, accountId comes as null. For the new version of api keys, we can get the accountId
      accountId = apiKeyService.getAccountIdFromApiKey(apiKey);
    }

    apiKeyService.validate(apiKey, accountId);
  }

  protected boolean authenticationExemptedRequests(ContainerRequestContext requestContext) {
    return requestContext.getMethod().equals(OPTIONS) || publicAPI() || isPublicApiWithWhitelist()
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/version")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger")
        || requestContext.getUriInfo().getAbsolutePath().getPath().endsWith("api/swagger.json");
  }

  private boolean isPublicApiWithWhitelist() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(PublicApiWithWhitelist.class) != null
        || resourceClass.getAnnotation(PublicApiWithWhitelist.class) != null;
  }

  protected boolean publicAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(PublicApi.class) != null
        || resourceClass.getAnnotation(PublicApi.class) != null;
  }

  private boolean isIdentityServiceRequest(ContainerRequestContext requestContext) {
    return identityServiceAPI()
        && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), IDENTITY_SERVICE_PREFIX);
  }

  protected boolean isIdentityServiceOriginatedRequest(ContainerRequestContext requestContext) {
    return startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), IDENTITY_SERVICE_PREFIX);
  }

  private boolean isExternalFacingApiRequest(ContainerRequestContext requestContext) {
    return externalFacingAPI();
  }

  boolean identityServiceAPI() {
    return resourceInfo.getResourceMethod().getAnnotation(IdentityServiceAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(IdentityServiceAuth.class) != null;
  }

  protected boolean delegateAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(DelegateAuth.class) != null
        || resourceClass.getAnnotation(DelegateAuth.class) != null;
  }

  protected boolean learningEngineServiceAPI() {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(LearningEngineAuth.class) != null
        || resourceClass.getAnnotation(LearningEngineAuth.class) != null;
  }

  protected boolean externalFacingAPI() {
    return resourceInfo.getResourceMethod().getAnnotation(ExternalFacingApiAuth.class) != null
        || resourceInfo.getResourceClass().getAnnotation(ExternalFacingApiAuth.class) != null;
  }

  protected boolean isApiKeyAuthorizationAPI() {
    return resourceInfo.getResourceMethod().getAnnotation(ApiKeyAuthorized.class) != null
        || resourceInfo.getResourceClass().getAnnotation(ApiKeyAuthorized.class) != null;
  }

  private String getRequestParamFromContext(
      String key, MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
    return queryParameters.getFirst(key) != null ? queryParameters.getFirst(key) : pathParameters.getFirst(key);
  }
}
