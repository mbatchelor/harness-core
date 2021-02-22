package io.harness.delegatetasks;

import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ValidateSecretManagerConfigurationTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
    private final EncryptedRecord encryptedRecord;
    private final EncryptionConfig encryptionConfig;

    @Override
    public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
        return ((SecretManagerConfig) encryptionConfig).fetchRequiredExecutionCapabilities(maskingEvaluator);
    }
}
