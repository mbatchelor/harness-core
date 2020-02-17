package software.wings.service.impl.security;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.persistence.UuidAware;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;
import io.harness.stream.BoundedInputStream;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SecretManagerConfig;
import software.wings.beans.SettingAttribute;
import software.wings.security.encryption.EncryptedData;
import software.wings.security.encryption.SecretChangeLog;
import software.wings.security.encryption.SecretUsageLog;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue.SettingVariableTypes;
import software.wings.settings.UsageRestrictions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Created by rsingh on 9/7/18.
 */
public class NoOpSecretManagerImpl implements SecretManager {
  @Override
  public List<SecretManagerConfig> listSecretManagers(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String secretsManagerConfigId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptionType getEncryptionType(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptionType getEncryptionBySecretManagerId(String kmsId, String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void maskEncryptedFields(EncryptableSetting object) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resetUnchangedEncryptedFields(EncryptableSetting sourceObject, EncryptableSetting destinationObject) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<SecretUsageLog> getUsageLogs(PageRequest<SecretUsageLog> pageRequest, String accountId,
      String entityId, SettingVariableTypes variableType) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SecretChangeLog> getChangeLogs(String accountId, String entityId, SettingVariableTypes variableType)
      throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String encrypt(String accountId, String secret, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData encrypt(String accountId, SettingVariableTypes settingType, char[] secret, String path,
      EncryptedData encryptedData, String secretName, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<EncryptedDataDetail> encryptedDataDetails(String accountId, String fieldName, String refId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(EncryptableSetting object) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(
      EncryptableSetting object, String appId, String workflowExecutionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String entityId, EncryptionType encryptionType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<SettingAttribute> listEncryptedSettingAttributes(String accountId) {
    return null;
  }

  @Override
  public Collection<SettingAttribute> listEncryptedSettingAttributes(String accountId, Set<String> categories) {
    return null;
  }

  @Override
  public String getEncryptedYamlRef(EncryptableSetting object, String... fieldName) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getEncryptedDataFromYamlRef(String encryptedYamlRef, String accountId) {
    return null;
  }

  @Override
  public boolean transitionSecrets(String accountId, EncryptionType fromEncryptionType, String fromSecretId,
      EncryptionType toEncryptionType, String toSecretId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void changeSecretManager(String accountId, String entityId, EncryptionType fromEncryptionType,
      String fromKmsId, EncryptionType toEncryptionType, String toKmsId) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretMappedToAccountByName(String accountId, String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretMappedToAppByName(String accountId, String appId, String envId, String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretById(String accountId, String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretByName(String accountId, String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String saveSecret(
      String accountId, String kmsId, String name, String value, String path, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> importSecrets(String accountId, List<SecretText> secretTexts) {
    return null;
  }

  @Override
  public List<String> importSecretsViaFile(String accountId, InputStream uploadStream) {
    return null;
  }

  @Override
  public boolean updateSecret(
      String accountId, String uuId, String name, String value, String path, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean updateUsageRestrictionsForSecretOrFile(
      String accountId, String uuId, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deleteSecret(String accountId, String uuId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deleteSecretUsingUuid(String uuId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String saveFile(String accountId, String kmsId, String name, long fileSize,
      UsageRestrictions usageRestrictions, BoundedInputStream inputStream) {
    throw new UnsupportedOperationException();
  }

  @Override
  public File getFile(String accountId, String uuId, File readInto) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] getFileContents(String accountId, String uuId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean updateFile(String accountId, String name, String uuid, long fileSize,
      UsageRestrictions usageRestrictions, BoundedInputStream inputStream) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deleteFile(String accountId, String uuId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<EncryptedData> listSecretsMappedToAccount(
      String accountId, PageRequest<EncryptedData> pageRequest, boolean details) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<UuidAware> getSecretUsage(String accountId, String secretTextId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String saveSecretUsingLocalMode(
      String accountId, String name, String value, String path, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean transitionAllSecretsToHarnessSecretManager(String accountId) {
    return false;
  }

  @Override
  public void clearDefaultFlagOfSecretManagers(String accountId) {}

  @Override
  public void deleteByAccountId(String accountId) {}
}
