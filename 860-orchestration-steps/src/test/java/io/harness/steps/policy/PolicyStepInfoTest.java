package io.harness.steps.policy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PolicyStepInfoTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDeserialize() throws IOException {
    String basicStepYaml = "name: myPolicyStep\n"
        + "identifier: myPolicyStep\n"
        + "type: Policy\n"
        + "timeout: 10m\n"
        + "spec:\n"
        + "  policySets:\n"
        + "  - acc.ps1\n"
        + "  - org.ps1\n"
        + "  - ps1\n"
        + "  type: Custom\n"
        + "  policySpec:\n"
        + "    payload: |\n"
        + "      {\n"
        + "        \"this\" : \"that\"\n"
        + "      }";
    StepElementConfig stepElementConfig = YamlUtils.read(basicStepYaml, StepElementConfig.class);
    assertThat(stepElementConfig).isNotNull();
  }
}