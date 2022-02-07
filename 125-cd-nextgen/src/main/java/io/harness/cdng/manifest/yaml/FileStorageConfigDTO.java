package io.harness.cdng.manifest.yaml;

public interface FileStorageConfigDTO {
  String getKind();

  FileStorageStoreConfig toFileStorageStoreConfig();
}
