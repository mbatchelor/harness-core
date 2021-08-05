package io.harness.cdng.creator.plan.steps;

import static io.harness.cdng.visitor.YamlTypes.K8S_CANARY_DELETE;
import static io.harness.cdng.visitor.YamlTypes.K8S_CANARY_DEPLOY;

import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.k8s.K8sCanaryDeleteStepParameters;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.utils.TimeoutUtils;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class K8sCanaryDeletePMSStepPlanCreator extends K8sRetryAdviserObtainment {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet("K8sCanaryDelete");
  }

  @Override
  protected StepParameters getStepParameters(PlanCreationContext ctx, StepElementConfig stepElement) {
    StepParameters stepParameters = stepElement.getStepSpecType().getStepParameters();
    if (stepElement.getStepSpecType() instanceof WithStepElementParameters) {
      stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
      stepParameters =
          ((WithStepElementParameters) stepElement.getStepSpecType())
              .getStepParametersInfo(stepElement,
                  getRollbackParameters(ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN));
    }

    String canaryStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_CANARY_DEPLOY);
    String canaryDeleteStepFqn = getExecutionStepFqn(ctx.getCurrentField(), K8S_CANARY_DELETE);
    K8sCanaryDeleteStepParameters canaryDeleteStepParameters =
        (K8sCanaryDeleteStepParameters) ((StepElementParameters) stepParameters).getSpec();
    canaryDeleteStepParameters.setCanaryStepFqn(canaryStepFqn);
    canaryDeleteStepParameters.setCanaryDeleteStepFqn(canaryDeleteStepFqn);

    return stepParameters;
  }
}
