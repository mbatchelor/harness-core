package io.harness.beans.steps.stepinfo;

import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.data.validator.EntityIdentifier;
import io.harness.facilitator.OrchestrationFacilitatorType;
import io.harness.pms.steps.StepType;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonView;
import java.beans.ConstructorProperties;
import java.util.Optional;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("restoreCache")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("restoreCacheStepInfo")
public class RestoreCacheStepInfo implements CIStepInfo {
  public static final int DEFAULT_RETRY = 0;
  public static final int DEFAULT_TIMEOUT = 1200;
  @JsonIgnore private String callbackId;
  @JsonView(JsonViews.Internal.class)
  @NotNull
  public static final TypeInfo typeInfo =
      TypeInfo.builder()
          .stepInfoType(CIStepInfoType.RESTORE_CACHE)
          .stepType(StepType.newBuilder().setType(CIStepInfoType.RESTORE_CACHE.name()).build())
          .build();

  @NotNull @EntityIdentifier private String identifier;
  private String name;
  @Min(MIN_RETRY) @Max(MAX_RETRY) private int retry;
  @Min(MIN_TIMEOUT) @Max(MAX_TIMEOUT) private int timeout;
  @NotNull private String key;
  private boolean failIfNotExist;

  @Builder
  @ConstructorProperties({"callbackId", "identifier", "name", "retry", "timeout", "key", "failIfNotExist"})
  public RestoreCacheStepInfo(String callbackId, String identifier, String name, Integer retry, Integer timeout,
      String key, boolean failIfNotExist) {
    this.callbackId = callbackId;
    this.identifier = identifier;
    this.name = name;
    this.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.timeout = Optional.ofNullable(timeout).orElse(DEFAULT_TIMEOUT);
    this.key = key;
    this.failIfNotExist = failIfNotExist;
  }

  @Override
  public TypeInfo getNonYamlInfo() {
    return typeInfo;
  }

  @Override
  public String getDisplayName() {
    return name;
  }

  @Override
  public StepType getStepType() {
    return typeInfo.getStepType();
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }
}
