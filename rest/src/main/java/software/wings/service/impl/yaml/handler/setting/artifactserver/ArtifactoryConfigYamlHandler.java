package software.wings.service.impl.yaml.handler.setting.artifactserver;

import software.wings.beans.SettingAttribute;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.ArtifactoryConfig.Yaml;
import software.wings.beans.yaml.ChangeContext;

import java.util.List;

/**
 * @author rktummala on 11/19/17
 */
public class ArtifactoryConfigYamlHandler extends ArtifactServerYamlHandler<Yaml, ArtifactoryConfig> {
  @Override
  public Yaml toYaml(SettingAttribute settingAttribute, String appId) {
    ArtifactoryConfig artifactoryConfig = (ArtifactoryConfig) settingAttribute.getValue();
    return new Yaml(artifactoryConfig.getType(), settingAttribute.getName(), artifactoryConfig.getArtifactoryUrl(),
        artifactoryConfig.getUsername(), getEncryptedValue(artifactoryConfig, "password", false));
  }

  protected SettingAttribute setWithYamlValues(
      SettingAttribute previous, ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String uuid = previous != null ? previous.getUuid() : null;
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    ArtifactoryConfig config = ArtifactoryConfig.builder()
                                   .accountId(accountId)
                                   .artifactoryUrl(yaml.getUrl())
                                   .password(null)
                                   .encryptedPassword(yaml.getPassword())
                                   .username(yaml.getUsername())
                                   .build();
    return buildSettingAttribute(accountId, yaml.getName(), uuid, config);
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }
}
