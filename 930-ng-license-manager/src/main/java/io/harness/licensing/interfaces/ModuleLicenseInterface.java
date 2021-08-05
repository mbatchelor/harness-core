package io.harness.licensing.interfaces;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.ModuleType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;

@OwnedBy(HarnessTeam.GTM)
public interface ModuleLicenseInterface {
  ModuleLicenseDTO generateTrialLicense(
      Edition edition, String accountId, LicenseType licenseType, ModuleType moduleType);
}
