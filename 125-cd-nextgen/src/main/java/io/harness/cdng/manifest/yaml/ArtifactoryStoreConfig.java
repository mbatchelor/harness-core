/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.artifactory.ArtifactoryFile;
import io.harness.filters.ConnectorRefExtractorHelper;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@JsonTypeName(ManifestStoreType.ARTIFACTORY)
@SimpleVisitorHelper(helperClass = ConnectorRefExtractorHelper.class)
@TypeAlias("artifactoryStore")
@RecasterAlias("io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig")
public class ArtifactoryStoreConfig implements FileStorageStoreConfig, Visitable, WithConnectorRef {
  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> connectorRef;
  @NotNull
  @Wither
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  private ParameterField<String> repositoryName;
  @ApiModelProperty(dataType = "[Lio.harness.cdng.manifest.yaml.ArtifactoryFromYaml;")
  @YamlSchemaTypes(value = {runtime})
  @NotNull
  @Wither
  @JsonProperty("artifacts")
  ParameterField<List<ArtifactoryFromYaml>> artifacts;

  @Override
  public String getKind() {
    return ManifestStoreType.ARTIFACTORY;
  }

  @Override
  public StoreConfig cloneInternal() {
    return ArtifactoryStoreConfig.builder()
        .connectorRef(connectorRef)
        .repositoryName(repositoryName)
        .artifacts(artifacts)
        .build();
  }

  @Override
  public ParameterField<String> getConnectorReference() {
    return connectorRef;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    connectorRefMap.put(YAMLFieldNameConstants.CONNECTOR_REF, connectorRef);
    return connectorRefMap;
  }

  @Override
  public StoreConfig applyOverrides(StoreConfig overrideConfig) {
    ArtifactoryStoreConfig artifactoryStoreConfig = (ArtifactoryStoreConfig) overrideConfig;
    ArtifactoryStoreConfig resultantArtifactoryStore = this;
    if (!ParameterField.isNull(artifactoryStoreConfig.getConnectorRef())) {
      resultantArtifactoryStore = resultantArtifactoryStore.withConnectorRef(artifactoryStoreConfig.getConnectorRef());
    }
    if (!ParameterField.isNull(artifactoryStoreConfig.getRepositoryName())) {
      resultantArtifactoryStore =
          resultantArtifactoryStore.withRepositoryName(artifactoryStoreConfig.getRepositoryName());
    }
    if (artifactoryStoreConfig.getArtifacts() != null) {
      resultantArtifactoryStore = resultantArtifactoryStore.withArtifacts(artifactoryStoreConfig.getArtifacts());
    }
    return resultantArtifactoryStore;
  }

  @Override
  public FileStorageConfigDTO toFileStorageConfigDTO() {
    return ArtifactoryStorageConfigDTO.builder()
        .connectorRef(ParameterFieldHelper.getParameterFieldValue(connectorRef))
        .repositoryName(ParameterFieldHelper.getParameterFieldValue(repositoryName))
        .artifacts(ParameterFieldHelper.getParameterFieldValue(artifacts)
                       .stream()
                       .map(artifactoryFromYaml
                           -> ArtifactoryFile.builder()
                                  .name(ParameterFieldHelper.getParameterFieldValue(
                                      artifactoryFromYaml.getArtifactFile().getName()))
                                  .path(ParameterFieldHelper.getParameterFieldValue(
                                      artifactoryFromYaml.getArtifactFile().getPath()))
                                  .build())
                       .collect(Collectors.toList()))
        .build();
  }
}
