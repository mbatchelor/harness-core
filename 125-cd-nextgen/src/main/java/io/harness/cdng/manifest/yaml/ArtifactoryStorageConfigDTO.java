package io.harness.cdng.manifest.yaml;

import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.delegate.beans.artifactory.ArtifactoryFile;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ArtifactoryStorageConfigDTO implements FileStorageConfigDTO {
  String connectorRef;
  String repositoryName;
  List<ArtifactoryFile> artifacts;

  @Override
  public String getKind() {
    return ManifestStoreType.ARTIFACTORY;
  }

  @Override
  public FileStorageStoreConfig toFileStorageStoreConfig() {
    return ArtifactoryStoreConfig.builder()
        .connectorRef(ParameterField.createValueField(connectorRef))
        .repositoryName(ParameterField.createValueField(repositoryName))
        .artifacts(ParameterField.createValueField(
            artifacts.stream()
                .map(artifactoryFile
                    -> ArtifactoryFromYaml.builder()
                           .artifactFile(ArtifactFile.builder()
                                             .name(ParameterField.createValueField(artifactoryFile.getName()))
                                             .path(ParameterField.createValueField(artifactoryFile.getPath()))
                                             .build())
                           .build())
                .collect(Collectors.toList())))
        .build();
  }
}
