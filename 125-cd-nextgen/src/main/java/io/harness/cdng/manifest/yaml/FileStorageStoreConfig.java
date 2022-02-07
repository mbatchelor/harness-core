package io.harness.cdng.manifest.yaml;

import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;

public interface FileStorageStoreConfig extends StoreConfig {
  FileStorageConfigDTO toFileStorageConfigDTO();
}
