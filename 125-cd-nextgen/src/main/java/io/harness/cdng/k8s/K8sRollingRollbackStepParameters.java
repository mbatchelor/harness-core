package io.harness.cdng.k8s;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.harness.beans.ParameterField;
import io.harness.common.SwaggerConstants;
import io.harness.executionplan.stepsdependency.StepDependencySpec;
import io.harness.state.io.StepParameters;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8sRollingRollbackStepParameters implements StepParameters {
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH) private ParameterField<Integer> timeout;
  @JsonIgnore Map<String, StepDependencySpec> stepDependencySpecs;
}
