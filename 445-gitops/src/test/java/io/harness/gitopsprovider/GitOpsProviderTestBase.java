/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitopsprovider;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.rule.LifecycleRule;

import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(GITOPS)
public abstract class GitOpsProviderTestBase extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public GitOpsTestRule gitOpsProviderTestBase = new GitOpsTestRule(lifecycleRule.getClosingFactory());
}
