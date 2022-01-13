/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator;

import io.harness.cvng.servicelevelobjective.beans.slotargetspec.SLOTargetSpec;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;

public interface SLOTargetTransformer<E extends ServiceLevelObjective.SLOTarget, T extends SLOTargetSpec> {
  E getSLOTarget(T spec);
  T getSLOTargetSpec(E entity);
}
