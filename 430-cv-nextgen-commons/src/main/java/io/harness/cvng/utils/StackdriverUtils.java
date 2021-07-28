package io.harness.cvng.utils;

import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.gcp.helpers.GcpCredentialsHelperService;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StackdriverUtils {
  public enum Scope {
    METRIC_SCOPE("https://www.googleapis.com/auth/monitoring.read"),
    LOG_SCOPE("https://www.googleapis.com/auth/logging.read");

    private final String value;

    Scope(final String v) {
      this.value = v;
    }
    public String getValue() {
      return this.value;
    }
  }

  public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

  private StackdriverUtils() {}

  private static GoogleCredential getGoogleCredential(GcpConnectorDTO gcpConnectorDTO) {
    try {
      if (gcpConnectorDTO.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
        GcpManualDetailsDTO gcpManualDetailsDTO = (GcpManualDetailsDTO) gcpConnectorDTO.getCredential().getConfig();
        return GcpCredentialsHelperService.getGoogleCredentialFromFile(
            gcpManualDetailsDTO.getSecretKeyRef().getDecryptedValue());
      } else {
        return GcpCredentialsHelperService.getApplicationDefaultCredentials();
      }
    } catch (IOException e) {
      log.error("Exception while fetching google credential", e);
      throw new IllegalStateException("Cannot fetch google credential");
    }
  }

  public static Map<String, Object> getCommonEnvVariables(GcpConnectorDTO gcpConnectorDTO, Scope scope) {
    GoogleCredential credential = getGoogleCredential(gcpConnectorDTO);
    Map<String, Object> envVariables = new HashMap<>();
    credential = credential.createScoped(Lists.newArrayList(scope.getValue()));
    try {
      credential.refreshToken();
    } catch (IOException e) {
      log.error("Exception while fetching token for google credential", e);
      throw new IllegalStateException("Cannot fetch google credential token");
    }
    String accessToken = credential.getAccessToken();
    envVariables.put("accessToken", accessToken);
    envVariables.put("project", credential.getServiceAccountProjectId());
    return envVariables;
  }

  public static <T> T checkForNullAndReturnValue(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }
}
