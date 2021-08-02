package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ParameterField;
import io.harness.beans.SwaggerConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@JsonTypeName("Inline")
@OwnedBy(CDC)
public class ShellScriptInlineSource implements ShellScriptBaseSource {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> script;

  @Override
  public String getType() {
    return "Inline";
  }
}
