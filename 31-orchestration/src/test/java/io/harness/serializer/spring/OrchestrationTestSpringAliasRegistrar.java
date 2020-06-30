package io.harness.serializer.spring;

import io.harness.OrchestrationBeansAliasRegistrar;
import io.harness.utils.DummyOutcome;
import io.harness.utils.steps.TestStepParameters;

import java.util.Map;

public class OrchestrationTestSpringAliasRegistrar implements OrchestrationBeansAliasRegistrar {
  @Override
  public void register(Map<String, Class<?>> orchestrationElements) {
    orchestrationElements.put("dummyOutcome25", DummyOutcome.class);
    orchestrationElements.put("TestStepParameters25", TestStepParameters.class);
  }
}
