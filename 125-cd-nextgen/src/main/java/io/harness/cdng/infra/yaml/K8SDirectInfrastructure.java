package io.harness.cdng.infra.yaml;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.beans.ParameterField;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.K8sDirectInfraMapping;
import io.harness.cdng.visitor.helpers.pipelineinfrastructure.K8SDirectInfrastructureVisitorHelper;
import io.harness.common.SwaggerConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@Builder
@JsonTypeName(InfrastructureKind.KUBERNETES_DIRECT)
@SimpleVisitorHelper(helperClass = K8SDirectInfrastructureVisitorHelper.class)
public class K8SDirectInfrastructure implements Infrastructure, Visitable {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> connectorIdentifier;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> namespace;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> releaseName;

  // For Visitor Framework Impl
  String metadata;

  @Override
  public InfraMapping getInfraMapping() {
    return K8sDirectInfraMapping.builder()
        .k8sConnector(connectorIdentifier.getValue())
        .namespace(namespace.getValue())
        .build();
  }

  @Override
  public String getKind() {
    return InfrastructureKind.KUBERNETES_DIRECT;
  }

  @Override
  public Infrastructure applyOverrides(Infrastructure overrideConfig) {
    K8SDirectInfrastructure config = (K8SDirectInfrastructure) overrideConfig;
    K8SDirectInfrastructure resultantInfra = this;
    if (config.getConnectorIdentifier() != null) {
      resultantInfra = resultantInfra.withConnectorIdentifier(config.getConnectorIdentifier());
    }
    if (config.getNamespace() != null) {
      resultantInfra = resultantInfra.withNamespace(config.getNamespace());
    }
    if (config.getReleaseName() != null) {
      resultantInfra = resultantInfra.withReleaseName(config.getReleaseName());
    }
    return resultantInfra;
  }
}
