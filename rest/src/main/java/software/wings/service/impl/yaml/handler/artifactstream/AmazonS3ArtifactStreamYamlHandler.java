package software.wings.service.impl.yaml.handler.artifactstream;

import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.AmazonS3ArtifactStream.Yaml;

/**
 * @author rktummala on 10/09/17
 */
public class AmazonS3ArtifactStreamYamlHandler extends ArtifactStreamYamlHandler<Yaml, AmazonS3ArtifactStream> {
  @Override
  public Yaml toYaml(AmazonS3ArtifactStream bean, String appId) {
    Yaml yaml = Yaml.builder().build();
    super.toYaml(yaml, bean);
    yaml.setArtifactPaths(bean.getArtifactPaths());
    yaml.setBucketName(bean.getJobname());
    return yaml;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  protected AmazonS3ArtifactStream getNewArtifactStreamObject() {
    return new AmazonS3ArtifactStream();
  }
}
