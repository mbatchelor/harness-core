package software.wings.scheduler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.beans.Application.GLOBAL_APP_ID;
import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;
import static software.wings.common.Constants.ACCOUNT_ID_KEY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ExceptionLogger;
import io.harness.scheduler.PersistentScheduler;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SyncTaskContext;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.LdapTestResponse.Status;
import software.wings.beans.sso.LdapUserResponse;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.dl.WingsPersistence;
import software.wings.features.LdapFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.logcontext.LdapGroupSyncJobLogContext;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;

/**
 * Periodic job which syncs the LDAP group users with the linked group in Harness for given SSO provider
 *
 * @author Swapnil
 */

@OwnedBy(PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
@Slf4j
public class LdapGroupSyncJob implements Job {
  private static final SecureRandom random = new SecureRandom();
  private static final String SSO_PROVIDER_ID_KEY = "ssoId";

  public static final String GROUP = "LDAP_GROUP_SYNC_CRON_JOB";
  private static final int POLL_INTERVAL = 900; // Seconds

  public static final long MIN_LDAP_SYNC_TIMEOUT = 60 * 1000L; // 1 minute
  public static final long MAX_LDAP_SYNC_TIMEOUT = 3 * 60 * 1000L; // 3 minute

  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject private PersistentLocker persistentLocker;
  @Inject private ExecutorService executorService;
  @Inject private SSOSettingService ssoSettingService;
  @Inject private SSOService ssoService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject private UserService userService;
  @Inject private UserGroupService userGroupService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AccountService accountService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject @Named(LdapFeature.FEATURE_NAME) private PremiumFeature ldapFeature;

  public static void addWithDelay(PersistentScheduler jobScheduler, String accountId, String ssoId) {
    // Add some randomness in the trigger start time to avoid overloading quartz by firing jobs at the same time.
    long startTime = System.currentTimeMillis() + random.nextInt((int) TimeUnit.SECONDS.toMillis(POLL_INTERVAL));
    addInternal(jobScheduler, accountId, ssoId, new Date(startTime));
  }

  public static void add(PersistentScheduler jobScheduler, String accountId, String ssoId) {
    addInternal(jobScheduler, accountId, ssoId, null);
  }

  private static void addInternal(
      PersistentScheduler jobScheduler, String accountId, String ssoId, Date triggerStartTime) {
    JobDetail job = JobBuilder.newJob(LdapGroupSyncJob.class)
                        .withIdentity(ssoId, GROUP)
                        .usingJobData(ACCOUNT_ID_KEY, accountId)
                        .usingJobData(SSO_PROVIDER_ID_KEY, ssoId)
                        .build();

    TriggerBuilder triggerBuilder =
        TriggerBuilder.newTrigger()
            .withIdentity(ssoId, GROUP)
            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(POLL_INTERVAL).repeatForever());
    if (triggerStartTime != null) {
      triggerBuilder.startAt(triggerStartTime);
    }

    jobScheduler.ensureJob__UnderConstruction(job, triggerBuilder.build());
  }

  public static void delete(
      PersistentScheduler jobScheduler, SSOSettingService ssoSettingService, String accountId, String ssoId) {
    jobScheduler.deleteJob(ssoId, GROUP);
    ssoSettingService.closeSyncFailureAlertIfOpen(accountId, ssoId);
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    try {
      String accountId = jobExecutionContext.getMergedJobDataMap().getString(ACCOUNT_ID_KEY);
      String ssoId = jobExecutionContext.getMergedJobDataMap().getString(SSO_PROVIDER_ID_KEY);
      LdapSettings settings = ssoSettingService.getLdapSettingsByUuid(ssoId);
      if (settings == null) {
        jobScheduler.deleteJob(ssoId, GROUP);
        return;
      }
      // The app level lock was a work around for the threading issue we observed in quartz scheduler. The execute() was
      // getting called on all the managers. Its supposed to call it only on one manager. This is a way to stop that
      // from happening.
      try (AcquiredLock lock = persistentLocker.tryToAcquireLock(LdapSettings.class, ssoId, Duration.ofSeconds(60))) {
        if (lock == null) {
          return;
        }
        executorService.submit(() -> executeInternal(accountId, ssoId));
      }
    } catch (WingsException exception) {
      // do nothing. Only one manager should acquire the lock.
    } catch (Exception e) {
      // Catching all exceptions to prevent immediate job retry.
      log.error("Error while trying to sync user group for ldap sso provider", e);
    }
  }

  private void updateRemovedGroupMembers(UserGroup userGroup, Collection<LdapUserResponse> expectedMembers,
      Map<UserGroup, Set<User>> removedGroupMembers) {
    if (isEmpty(userGroup.getMembers())) {
      return;
    }

    Set<String> expectedMemberEmails =
        expectedMembers.stream().map(LdapUserResponse::getEmail).filter(Objects::nonNull).collect(Collectors.toSet());

    Set<User> removedUsers = userGroup.getMembers()
                                 .stream()
                                 .filter(member -> !expectedMemberEmails.contains(member.getEmail()))
                                 .collect(Collectors.toSet());

    if (!removedGroupMembers.containsKey(userGroup)) {
      removedGroupMembers.put(userGroup, Sets.newHashSet());
    }
    log.info("LDAPIterator: Removing users {} as part of sync with usergroup {} in accountId {}", removedUsers,
        userGroup.getUuid(), userGroup.getAccountId());
    removedGroupMembers.getOrDefault(userGroup, Sets.newHashSet()).addAll(removedUsers);
  }

  private void updateAddedGroupMembers(UserGroup userGroup, Collection<LdapUserResponse> expectedMembers,
      Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers) {
    Set<String> existingUserEmails;
    if (isEmpty(userGroup.getMembers())) {
      existingUserEmails = Sets.newHashSet();
    } else {
      existingUserEmails = userGroup.getMembers().stream().map(User::getEmail).collect(Collectors.toSet());
    }

    expectedMembers.stream()
        .filter(member -> member.getEmail() != null && !existingUserEmails.contains(member.getEmail()))
        .forEach(member -> {
          if (!addedGroupMembers.containsKey(member)) {
            addedGroupMembers.put(member, Sets.newHashSet());
          }
          addedGroupMembers.get(member).add(userGroup);
        });
  }

  @VisibleForTesting
  UserGroup syncUserGroupMetadata(UserGroup userGroup, LdapGroupResponse groupResponse) {
    UpdateOperations<UserGroup> updateOperations = wingsPersistence.createUpdateOperations(UserGroup.class);
    setUnset(updateOperations, UserGroupKeys.ssoGroupName, groupResponse.getName());
    log.info("LDAP: sync setting ssGroupName to {}", groupResponse.getName());
    Query<UserGroup> query = wingsPersistence.createQuery(UserGroup.class)
                                 .filter("_id", userGroup.getUuid())
                                 .field(UserGroupKeys.accountId)
                                 .equal(userGroup.getAccountId());
    return wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
  }

  private void syncUserGroupMembers(String accountId, Map<UserGroup, Set<User>> removedGroupMembers,
      Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers) {
    removedGroupMembers.forEach((userGroup, users) -> userGroupService.removeMembers(userGroup, users, false, true));
    for (Map.Entry<LdapUserResponse, Set<UserGroup>> entry : addedGroupMembers.entrySet()) {
      LdapUserResponse ldapUserResponse = entry.getKey();
      Set<UserGroup> userGroups = entry.getValue();

      User user = userService.getUserByEmail(ldapUserResponse.getEmail());
      if (user != null && userService.isUserAssignedToAccount(user, accountId)) {
        userService.addUserToUserGroups(accountId, user, Lists.newArrayList(userGroups), true, true);
      } else {
        UserInvite userInvite = anUserInvite()
                                    .withAccountId(accountId)
                                    .withEmail(ldapUserResponse.getEmail())
                                    .withName(ldapUserResponse.getName())
                                    .withUserGroups(Lists.newArrayList(userGroups))
                                    .build();
        userService.inviteUser(userInvite, false, true);
      }
    }
  }

  @VisibleForTesting
  Map<String, Set<UserGroup>> getUserGroupsByEmailMap(
      String accountId, Map<LdapUserResponse, Set<UserGroup>> addedGroups, Map<UserGroup, Set<User>> removedGroups) {
    Set<User> usersSet = new HashSet<>();
    for (Set<User> userSet : removedGroups.values()) {
      usersSet.addAll(userSet);
    }
    Map<String, Set<UserGroup>> emailToUserGroups = new HashMap<>();
    addedGroups.forEach((member, userGroups) -> {
      emailToUserGroups.computeIfAbsent(member.getEmail(), k -> new HashSet<>()).addAll(userGroups);
      User user = userService.getUserByEmail(member.getEmail());
      if (user != null) {
        usersSet.add(user);
      }
    });
    userService.loadUserGroupsForUsers(new ArrayList<>(usersSet), accountId);
    usersSet.forEach(user -> {
      if (emailToUserGroups.containsKey(user.getEmail())) {
        emailToUserGroups.get(user.getEmail()).addAll(user.getUserGroups());
      }
    });
    removedGroups.forEach((userGroup, users) -> users.forEach(user -> {
      emailToUserGroups.computeIfAbsent(user.getEmail(), k -> new HashSet<>(user.getUserGroups()));
      emailToUserGroups.get(user.getEmail()).remove(userGroup);
    }));

    return emailToUserGroups;
  }

  @VisibleForTesting
  LdapGroupResponse fetchGroupDetails(
      LdapSettings ldapSettings, EncryptedDataDetail encryptedDataDetail, UserGroup userGroup) {
    long userProvidedTimeout = ldapSettings.getConnectionSettings().getResponseTimeout();
    // if user specified time
    long ldapSyncTimeout = getLdapSyncTimeout(userProvidedTimeout);
    log.info("Fetching LDAP user group {} group details for {} with timeout {}", userGroup.getName(),
        ldapSettings.getAccountId(), ldapSyncTimeout);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(ldapSettings.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(ldapSyncTimeout)
                                          .build();
    LdapGroupResponse groupResponse = delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
                                          .fetchGroupByDn(ldapSettings, encryptedDataDetail, userGroup.getSsoGroupId());
    if (null == groupResponse) {
      String message = String.format(LdapConstants.USER_GROUP_SYNC_INVALID_REMOTE_GROUP, userGroup.getName());
      log.info("LDAP : Group Response for group {} from delegate is null", userGroup.getName());
      throw new WingsException(ErrorCode.USER_GROUP_SYNC_FAILURE, message);
    }
    log.info("LDAP : Group Response for group {} from delegate {}", userGroup.getName(), groupResponse);
    return groupResponse;
  }

  @VisibleForTesting
  public long getLdapSyncTimeout(long userProvidedTimeout) {
    if (userProvidedTimeout < MIN_LDAP_SYNC_TIMEOUT) {
      return MIN_LDAP_SYNC_TIMEOUT;
    } else {
      return Math.min(userProvidedTimeout, MAX_LDAP_SYNC_TIMEOUT);
    }
  }

  @VisibleForTesting
  boolean validateUserGroupStates(Collection<UserGroup> userGroups) {
    for (UserGroup userGroup : userGroups) {
      log.info("[LdapGroupSyncJob] Validating user group {} state before sync", userGroup.getName());
      UserGroup savedUserGroup = userGroupService.get(userGroup.getAccountId(), userGroup.getUuid(), false);
      if (!savedUserGroup.isSsoLinked()) {
        log.info(
            "[LdapGroupSyncJob] Validating UserGroup {} state before sync And Failed because group is not ssolinked",
            userGroup.getName());
        return false;
      }
      if (!savedUserGroup.getSsoGroupId().equals(userGroup.getSsoGroupId())) {
        log.info(
            "[LdapGroupSyncJob] Validating user group {} state before sync And Failed because mismatch in SavedUserGroupId:{} and UserGroupId:{} and UserGroupName:{}",
            savedUserGroup.getSsoGroupId(), userGroup.getSsoGroupId(), userGroup.getName());
        return false;
      }
    }
    log.info("[LdapGroupSyncJob] All UserGroups are valid sync can proceed : UserGroups {}", userGroups);
    return true;
  }

  @VisibleForTesting
  void syncUserGroups(String accountId, LdapSettings ldapSettings, List<UserGroup> userGroups, String ssoId) {
    Map<UserGroup, Set<User>> removedGroupMembers = new HashMap<>();
    Map<LdapUserResponse, Set<UserGroup>> addedGroupMembers = new HashMap<>();

    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);

    List<UserGroup> userGroupsFailedToSync = new ArrayList<>();
    for (UserGroup userGroup : userGroups) {
      try {
        log.info("Ldap started syncing usergroup {}", userGroup.getName());
        LdapGroupResponse groupResponse = fetchGroupDetails(ldapSettings, encryptedDataDetail, userGroup);

        if (!groupResponse.isSelectable()) {
          String message = String.format(
              LdapConstants.USER_GROUP_SYNC_NOT_ELIGIBLE, userGroup.getName(), groupResponse.getMessage());
          throw new UnsupportedOperationException(message);
        }
        syncUserGroupMetadata(userGroup, groupResponse);

        updateRemovedGroupMembers(userGroup, groupResponse.getUsers(), removedGroupMembers);
        updateAddedGroupMembers(userGroup, groupResponse.getUsers(), addedGroupMembers);
      } catch (Exception e) {
        log.error("LDAP sync failed for userGroup {}", userGroup.getName(), e);
        userGroupsFailedToSync.add(userGroup);
      }
    }

    if (userGroupsFailedToSync.isEmpty()) {
      ssoSettingService.closeSyncFailureAlertIfOpen(accountId, ssoId);
    } else {
      String userGroupsFailed =
          userGroupsFailedToSync.stream().map(UserGroup::getName).collect(Collectors.joining(", ", "[", "]"));
      ssoSettingService.raiseSyncFailureAlert(
          accountId, ssoId, String.format("Ldap Sync failed for groups: %s", userGroupsFailed));
    }
    // Sync the groups only if the state is still the same as we started. Else any change in the groups would have
    // already triggered another cron job and it will handle it.
    if (validateUserGroupStates(userGroups)) {
      log.info("LDAP: removedGroupMembers for ssoId {} is {}", ssoId, removedGroupMembers);
      log.info("LDAP: addedGroupMembers for ssoId {} is {}", ssoId, addedGroupMembers);
      syncUserGroupMembers(accountId, removedGroupMembers, addedGroupMembers);
    }
  }

  private void executeInternal(String accountId, String ssoId) {
    if (!ldapFeature.isAvailableForAccount(accountId)) {
      log.info("Skipping LDAP sync ssoId {} accountId {}", ssoId, accountId);
      return;
    }

    if (featureFlagService.isEnabled(FeatureName.LDAP_GROUP_SYNC_JOB_ITERATOR, accountId)) {
      log.info("LDAP_GROUP_SYNC_JOB_ITERATOR LDAP sync not for ssoId {} accountId {}", ssoId, accountId);
      return;
    }

    LdapSettings ldapSettings = ssoSettingService.getLdapSettingsByUuid(ssoId);
    if (ldapSettings == null) {
      jobScheduler.deleteJob(ssoId, GROUP);
      ssoSettingService.closeSyncFailureAlertIfOpen(accountId, ssoId);
      return;
    }

    try (AutoLogContext ignore = new LdapGroupSyncJobLogContext(accountId, ssoId, OVERRIDE_ERROR)) {
      log.info("Executing ldap group sync job for ssoId: {} and accountId: {}", ssoId, accountId);

      LdapTestResponse ldapTestResponse = ssoService.validateLdapConnectionSettings(ldapSettings, accountId);
      if (ldapTestResponse.getStatus() == Status.FAILURE) {
        if (ssoSettingService.isDefault(accountId, ssoId)) {
          ssoSettingService.sendSSONotReachableNotification(accountId, ldapSettings);
        } else {
          ssoSettingService.raiseSyncFailureAlert(
              accountId, ssoId, String.format(LdapConstants.SSO_PROVIDER_NOT_REACHABLE, ldapSettings.getDisplayName()));
        }
        return;
      }

      List<UserGroup> userGroupsToSync = userGroupService.getUserGroupsBySsoId(accountId, ssoId);
      syncUserGroups(accountId, ldapSettings, userGroupsToSync, ssoId);

      log.info(
          "Ldap group sync job done for ssoId {} accountId {} and usergroups {}", ssoId, accountId, userGroupsToSync);
    } catch (WingsException exception) {
      if (exception.getCode() == ErrorCode.USER_GROUP_SYNC_FAILURE) {
        ssoSettingService.raiseSyncFailureAlert(accountId, ssoId, exception.getMessage());
      } else {
        ssoSettingService.raiseSyncFailureAlert(accountId, ssoId,
            String.format(LdapConstants.USER_GROUP_SYNC_FAILED, ldapSettings.getDisplayName())
                + exception.getMessage());
      }
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception ex) {
      ssoSettingService.raiseSyncFailureAlert(accountId, ssoId,
          String.format(LdapConstants.USER_GROUP_SYNC_FAILED, ldapSettings.getDisplayName()) + ex.getMessage());
      log.error("Error while syncing ssoId {} in accountId {}", ssoId, accountId, ex);
    }
  }
}