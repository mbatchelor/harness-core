package io.harness.plancreator.pipeline;

import io.harness.beans.ParameterField;
import io.harness.common.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.plancreator.stages.StageElementWrapperConfig;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@AllArgsConstructor
@TypeAlias("pipelineInfoConfig")
public class PipelineInfoConfig {
  String uuid;
  @EntityName String name;
  @EntityIdentifier String identifier;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;
  Map<String, String> tags;

  List<NGVariable> variables;
  CodeBase ciCodebase;

  @Singular List<StageElementWrapperConfig> stages;
}
