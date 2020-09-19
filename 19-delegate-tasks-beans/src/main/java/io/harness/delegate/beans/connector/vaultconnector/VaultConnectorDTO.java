package io.harness.delegate.beans.connector.vaultconnector;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.security.encryption.AccessType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString(exclude = {"authToken", "secretId"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class VaultConnectorDTO extends ConnectorConfigDTO {
  private String authToken;
  private String basePath;
  private String vaultUrl;
  private boolean isReadOnly;
  private int renewIntervalHours;
  private String secretEngineName;
  private String appRoleId;
  private String secretId;
  private boolean isDefault;
  private int secretEngineVersion;

  @JsonIgnore
  public AccessType getAccessType() {
    return isNotEmpty(appRoleId) ? AccessType.APP_ROLE : AccessType.TOKEN;
  }
}
