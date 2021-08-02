package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ParameterField;
import io.harness.delegate.task.k8s.DeleteResourcesType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@OwnedBy(CDP)
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
public interface DeleteResourcesBaseSpec {
  @JsonIgnore DeleteResourcesType getType();
  @JsonIgnore String getResourceNamesValue();
  @JsonIgnore String getManifestPathsValue();
  @JsonIgnore Boolean getAllManifestPathsValue();
  @JsonIgnore
  default ParameterField<Boolean> getDeleteNamespaceParameterField() {
    return null;
  }
}
