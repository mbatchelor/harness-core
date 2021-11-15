package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthorityCount;
import static io.harness.remote.client.NGRestUtils.getResponse;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.Application.GLOBAL_APP_ID;

import io.harness.ModuleType;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseStatus;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO;
import io.harness.licensing.beans.modules.CDModuleLicenseDTO.CDModuleLicenseDTOBuilder;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.beans.modules.types.CDLicenseType;
import io.harness.licensing.remote.admin.AdminLicenseHttpClient;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.LicenseInfo;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class CDPaidLicenseToNGMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AdminLicenseHttpClient adminLicenseHttpClient;
  @Inject private AccountService accountService;
  private Queue<Account> retryQueue = new LinkedList<>();
  private Set<String> migrationType = Sets.newHashSet("ESSENTIALS", "PAID");
  private int maxRetryTimes = 600;

  @Override
  public void migrate() {
    Query<Account> query = wingsPersistence.createQuery(Account.class, excludeAuthorityCount)
                               .filter(ApplicationKeys.appId, GLOBAL_APP_ID)
                               .project(AccountKeys.uuid, true);
    query.and(query.criteria(ApplicationKeys.uuid).notEqual(GLOBAL_ACCOUNT_ID));

    try (HIterator<Account> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        Account record = records.next();
        Account account = accountService.get(record.getUuid());

        try {
          checkExistingNGLicenseAndMigrate(account);
        } catch (Exception e) {
          log.error("Failed to get CD summary info during CD License migration", e);
          log.error("Put account {} in retry list ", account.getUuid());
          retryQueue.add(account);
          try {
            Thread.sleep(60000);
          } catch (InterruptedException interruptedException) {
            log.error("Thread sleep error", interruptedException);
          }
        }
      }
    }

    while (!retryQueue.isEmpty()) {
      Account account = retryQueue.poll();
      try {
        checkExistingNGLicenseAndMigrate(account);
      } catch (Exception e) {
        log.error("Failed on CD license migration during retry", e);
        log.error("Put account {} in retry list again", account.getUuid());
        retryQueue.add(account);

        if (maxRetryTimes == 0) {
          log.error("Still failed after retried 10 hours, stop the migration");
          break;
        }
        maxRetryTimes--;

        try {
          Thread.sleep(60000);
        } catch (InterruptedException interruptedException) {
          log.error("Thread sleep error", interruptedException);
        }
      }
    }
    log.info("Migration for CD Paid license to NG Finished");
  }

  private void checkExistingNGLicenseAndMigrate(Account account) {
    log.info("Working on migrate CG CD license for account {}", account.getUuid());
    AccountLicenseDTO accountLicenseDTO = getResponse(adminLicenseHttpClient.getAccountLicense(account.getUuid()));
    LicenseInfo licenseInfo = account.getLicenseInfo();

    if (migrationType.contains(licenseInfo.getAccountType())) {
      log.info("Account {} requires a migrate", account.getUuid());
      // Only migrate PAID and ESSENTIALS
      if (accountLicenseDTO.getAllModuleLicenses().get(ModuleType.CD) == null) {
        // no ng cd license, create one
        log.info("Account {} doesn't have a NG CD license, create a new one", account.getUuid());
        createNewNGLicense(licenseInfo, account.getUuid());
      } else {
        // already has cd ng license
        log.info("Account {} has NG CD license, update existing", account.getUuid());
        List<ModuleLicenseDTO> moduleLicenseDTOS = accountLicenseDTO.getAllModuleLicenses().get(ModuleType.CD);
        CDModuleLicenseDTO latestCDLicense = getLatestCDLicense(moduleLicenseDTOS);

        updateExitingNGLicense(licenseInfo, latestCDLicense);
      }
    }
  }

  private void createNewNGLicense(LicenseInfo licenseInfo, String accountId) {
    CDModuleLicenseDTOBuilder<?, ?> builder = CDModuleLicenseDTO.builder()
                                                  .cdLicenseType(CDLicenseType.SERVICE_INSTANCES)
                                                  .serviceInstances(licenseInfo.getLicenseUnits())
                                                  .accountIdentifier(accountId)
                                                  .moduleType(ModuleType.CD);
    setLicenseDetails(licenseInfo.getAccountType(), licenseInfo.getExpiryTime(), builder);
    ModuleLicenseDTO response = getResponse(adminLicenseHttpClient.createAccountLicense(accountId, builder.build()));
    log.info("CDModuleLicense {} created", response.getId());
  }

  private void updateExitingNGLicense(LicenseInfo licenseInfo, CDModuleLicenseDTO latestCDLicense) {
    if (!LicenseType.PAID.equals(latestCDLicense.getLicenseType())) {
      // update license if ng cd is not paid
      compareAndUpdateLicense(licenseInfo, latestCDLicense);
      getResponse(adminLicenseHttpClient.updateModuleLicense(
          latestCDLicense.getId(), latestCDLicense.getAccountIdentifier(), latestCDLicense));
      log.info("CDModuleLicense {} updated", latestCDLicense.getId());
    }
  }

  private void setLicenseDetails(String accountType, long expiryTime, CDModuleLicenseDTOBuilder builder) {
    switch (accountType) {
      case "ESSENTIALS":
        builder.edition(Edition.TEAM);
        builder.licenseType(LicenseType.PAID);
        builder.expiryTime(expiryTime);
        break;
      case "PAID":
        builder.edition(Edition.ENTERPRISE);
        builder.licenseType(LicenseType.PAID);
        builder.expiryTime(expiryTime);
        break;
      default:
        break;
    }
    long currentTime = Instant.now().toEpochMilli();
    if (expiryTime > currentTime) {
      builder.status(LicenseStatus.ACTIVE);
    } else {
      builder.status(LicenseStatus.EXPIRED);
    }
  }

  private void compareAndUpdateLicense(LicenseInfo licenseInfo, CDModuleLicenseDTO licenseDTO) {
    switch (licenseInfo.getAccountType()) {
      case "ESSENTIALS":
        licenseDTO.setEdition(Edition.TEAM);
        break;
      case "PAID":
        licenseDTO.setEdition(Edition.ENTERPRISE);
        break;
      default:
        break;
    }
    licenseDTO.setLicenseType(LicenseType.PAID);
    licenseDTO.setExpiryTime(licenseInfo.getExpiryTime());
    licenseDTO.setServiceInstances(licenseInfo.getLicenseUnits());
    licenseDTO.setWorkloads(0);
    licenseDTO.setCdLicenseType(CDLicenseType.SERVICE_INSTANCES);

    long currentTime = Instant.now().toEpochMilli();
    if (licenseInfo.getExpiryTime() > currentTime) {
      licenseDTO.setStatus(LicenseStatus.ACTIVE);
    } else {
      licenseDTO.setStatus(LicenseStatus.EXPIRED);
    }
  }

  private CDModuleLicenseDTO getLatestCDLicense(List<ModuleLicenseDTO> licenses) {
    ModuleLicenseDTO result = null;
    for (ModuleLicenseDTO license : licenses) {
      if (result == null) {
        result = license;
      }

      if (result.getExpiryTime() < license.getExpiryTime()) {
        result = license;
      }
    }
    return (CDModuleLicenseDTO) result;
  }
}