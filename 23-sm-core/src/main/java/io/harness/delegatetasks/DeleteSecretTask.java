package io.harness.delegatetasks;

import com.google.inject.Inject;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class DeleteSecretTask extends AbstractDelegateRunnableTask {
  @Inject VaultEncryptorsRegistry vaultEncryptorsRegistry;

  public DeleteSecretTask(
      DelegateTaskPackage delegateTaskPackage, Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return run((DeleteSecretTaskParameters) parameters[0]);
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    return run((DeleteSecretTaskParameters) parameters);
  }

  private DeleteSecretTaskResponse run(DeleteSecretTaskParameters deleteSecretTaskParameters) {
    EncryptionConfig encryptionConfig = deleteSecretTaskParameters.getEncryptionConfig();
    EncryptedRecord encryptedRecord = deleteSecretTaskParameters.getExistingRecord();
    VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType());
    boolean deleted = vaultEncryptor.deleteSecret(encryptionConfig.getAccountId(), encryptedRecord, encryptionConfig);
    return DeleteSecretTaskResponse.builder().deleted(deleted).build();
  }
}
