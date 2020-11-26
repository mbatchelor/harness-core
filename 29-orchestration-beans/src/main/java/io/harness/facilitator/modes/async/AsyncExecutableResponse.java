package io.harness.facilitator.modes.async;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.Redesign;
import io.harness.annotations.dev.OwnedBy;
import io.harness.facilitator.modes.ExecutableResponse;

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Value
@Builder
@Redesign
@TypeAlias("asyncExecutableResponse")
public class AsyncExecutableResponse implements ExecutableResponse {
  @NonNull @Singular List<String> callbackIds;
}
