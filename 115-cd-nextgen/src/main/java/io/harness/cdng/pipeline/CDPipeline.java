package io.harness.cdng.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.cdng.pipeline.CDPipeline.CDPipelineKeys;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.mongo.index.Field;
import io.harness.ng.RsqlQueryable;
import io.harness.yaml.core.Tag;
import io.harness.yaml.core.auxiliary.intfc.StageElementWrapper;
import io.harness.yaml.core.intfc.Pipeline;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Value
@Builder
@FieldNameConstants(innerTypeName = "CDPipelineKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@RsqlQueryable(fields =
    {
      @Field(CDPipelineKeys.name)
      , @Field(CDPipelineKeys.identifier), @Field(CDPipelineKeys.description), @Field(CDPipelineKeys.tags),
          @Field(CDPipelineKeys.stages)
    })
public class CDPipeline implements Pipeline {
  @EntityName String name;
  @EntityIdentifier String identifier;
  String description;
  List<Tag> tags;
  @Singular List<StageElementWrapper> stages;
}
