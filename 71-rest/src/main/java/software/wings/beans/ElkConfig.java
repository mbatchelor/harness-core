package software.wings.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.annotation.EncryptableSetting;
import software.wings.audit.ResourceType;
import software.wings.jersey.JsonViews;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.analysis.ElkValidationType;
import software.wings.service.impl.analysis.ElkValidationTypeProvider;
import software.wings.settings.SettingValue;
import software.wings.settings.UsageRestrictions;
import software.wings.stencils.DefaultValue;
import software.wings.stencils.EnumData;
import software.wings.yaml.setting.VerificationProviderYaml;

import java.util.Arrays;
import java.util.List;

/**
 * The type ELK config.
 */
@JsonTypeName("ELK")
@Data
@Builder
@ToString(exclude = "password")
@EqualsAndHashCode(callSuper = false)
public class ElkConfig extends SettingValue implements EncryptableSetting {
  @Attributes(required = true, title = "Connector type")
  @DefaultValue("ELASTIC_SEARCH_SERVER")
  private ElkConnector elkConnector;

  @Attributes(title = "URL", required = true) @NotEmpty private String elkUrl;

  @Attributes(title = "Username") private String username;

  @Attributes(title = "Password") @Encrypted private char[] password;

  @SchemaIgnore @NotEmpty private String accountId;

  @Default private String kibanaVersion = "0";

  @JsonView(JsonViews.Internal.class) @SchemaIgnore private String encryptedPassword;

  @Attributes(required = true, title = "Authentication") private ElkValidationType validationType;

  @Attributes(required = true, title = "Authentication")
  @EnumData(enumDataProvider = ElkValidationTypeProvider.class)
  public ElkValidationType getValidationType() {
    return validationType;
  }

  public void setElkValidationType(ElkValidationType validationType) {
    this.validationType = validationType;
  }
  /**
   * Instantiates a new Elk config.
   */
  public ElkConfig() {
    super(SettingVariableTypes.ELK.name());
  }

  private ElkConfig(ElkConnector elkConnector, String elkUrl, String username, char[] password, String accountId,
      String kibanaVersion, String encryptedPassword, ElkValidationType validationType) {
    this();
    this.elkConnector = elkConnector;
    this.elkUrl = elkUrl;
    this.username = username;
    this.password = password;
    this.accountId = accountId;
    this.kibanaVersion = kibanaVersion;
    this.encryptedPassword = encryptedPassword;
    this.validationType = validationType;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities() {
    return Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(elkUrl));
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.VERIFICATION_PROVIDER.name();
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends VerificationProviderYaml {
    private String elkUrl;
    private String username;
    private String password;
    private String connectorType;
    private ElkValidationType validationType;

    @Builder
    public Yaml(String type, String harnessApiVersion, String elkUrl, ElkValidationType validationType, String username,
        String password, String connectorType, UsageRestrictions.Yaml usageRestrictions) {
      super(type, harnessApiVersion, usageRestrictions);
      this.elkUrl = elkUrl;
      this.validationType = validationType;
      this.username = username;
      this.password = password;
      this.connectorType = connectorType;
    }
  }
}
