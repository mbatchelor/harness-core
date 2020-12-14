package io.harness.serializer.spring.converters.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.orchestration.persistence.ProtoWriteConverter;
import io.harness.pms.contracts.steps.StepType;

import com.google.inject.Singleton;
import org.springframework.data.convert.WritingConverter;

@OwnedBy(CDC)
@Singleton
@WritingConverter
public class StepTypeWriteConverter extends ProtoWriteConverter<StepType> {}
