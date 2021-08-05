package software.wings.beans.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
@Value
@Builder
@JsonTypeName("WorkflowAction")
public class WorkflowAction implements Action {
  @NotEmpty private String workflowId;
  @NotEmpty private ActionType actionType = ActionType.WORKFLOW;
  private TriggerArgs triggerArgs;
  @Transient private String workflowName;
}
