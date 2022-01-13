/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.migration.MigrationDetails;
import io.harness.migration.MigrationProvider;
import io.harness.migration.entities.NGSchema;
import io.harness.ng.core.migration.schema.NGBeansSchema;

import java.util.ArrayList;
import java.util.List;

public class NGBeanMigrationProvider implements MigrationProvider {
  @Override
  public String getServiceName() {
    return "NGBeans";
  }

  @Override
  public Class<? extends NGSchema> getSchemaClass() {
    return NGBeansSchema.class;
  }

  @Override
  public List<Class<? extends MigrationDetails>> getMigrationDetailsList() {
    return new ArrayList<Class<? extends MigrationDetails>>() {
      { add(NGBeansTimeScaleMigrationDetails.class); }
    };
  }
}
