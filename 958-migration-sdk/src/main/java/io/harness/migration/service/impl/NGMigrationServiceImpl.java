package io.harness.migration.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.migration.entities.NGSchema.NG_SCHEMA_ID;

import static java.time.Duration.ofMinutes;

import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.migration.MigrationDetails;
import io.harness.migration.MigrationProvider;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;
import io.harness.migration.beans.NGMigrationConfiguration;
import io.harness.migration.entities.NGSchema;
import io.harness.migration.entities.NGSchema.NGSchemaKeys;
import io.harness.migration.service.NGMigrationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(DX)
public class NGMigrationServiceImpl implements NGMigrationService {
  private PersistentLocker persistentLocker;
  private ExecutorService executorService;
  private TimeLimiter timeLimiter;
  private Injector injector;
  private MongoTemplate mongoTemplate;

  @Override
  public void runMigrations(NGMigrationConfiguration configuration) {
    List<Class<? extends MigrationProvider>> migrationProviderList = configuration.getMigrationProviderList();
    Microservice microservice = configuration.getMicroservice();

    try (AcquiredLock lock = persistentLocker.waitToAcquireLock(
             NGSchema.class, NG_SCHEMA_ID + microservice, ofMinutes(25), ofMinutes(27))) {
      if (lock == null) {
        throw new GeneralException("The persistent lock was not acquired. That very unlikely, but yet it happened.");
      }

      for (Class<? extends MigrationProvider> migrationProvider : migrationProviderList) {
        MigrationProvider migrationProviderInstance = injector.getInstance(migrationProvider);
        String serviceName = migrationProviderInstance.getServiceName();
        //        String collectionName = migrationProviderInstance.getCollectionName();
        List<Class<? extends MigrationDetails>> migrationDetailsList =
            migrationProviderInstance.getMigrationDetailsList();

        log.info("[Migration] - Checking for new migrations");
        Class<? extends NGSchema> schemaClass = migrationProviderInstance.getSchemaClass();
        NGSchema schema = mongoTemplate.findOne(new Query(), schemaClass);
        List<MigrationType> migrationTypes = migrationProviderInstance.getMigrationDetailsList()
                                                 .stream()
                                                 .map(item -> injector.getInstance(item).getMigrationTypeName())
                                                 .collect(Collectors.toList());

        if (schema == null) {
          Map<MigrationType, Integer> migrationTypesWithVersion =
              migrationTypes.stream().collect(Collectors.toMap(Function.identity(), e -> 0));
          schema = NGSchema.builder()
                       .name(migrationProviderInstance.getServiceName())
                       .migrationDetails(migrationTypesWithVersion)
                       .build();
          String collectionName = mongoTemplate.getCollectionName(migrationProviderInstance.getSchemaClass());
          mongoTemplate.save(schema, collectionName);
        }

        for (Class<? extends MigrationDetails> migrationDetail : migrationDetailsList) {
          MigrationDetails migrationDetailInstance = injector.getInstance(migrationDetail);
          List<Pair<Integer, Class<? extends NGMigration>>> migrationsList = migrationDetailInstance.getMigrations();
          Map<Integer, Class<? extends NGMigration>> migrations =
              migrationsList.stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));

          int maxVersion = migrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

          Map<MigrationType, Integer> allSchemaMigrations = schema.getMigrationDetails();
          int currentVersion = allSchemaMigrations.getOrDefault(migrationDetailInstance.getMigrationTypeName(), 0);

          boolean isBackground = migrationDetailInstance.isBackground();

          runMigrationsInner(isBackground, currentVersion, maxVersion, migrations, migrationDetailInstance,
              migrationProviderInstance, serviceName, microservice);
        }
      }
    } catch (Exception e) {
      log.error("[Migration] - {} : Migration failed.", microservice, e);
    }
  }

  private void runMigrationsInner(boolean isBackground, int currentVersion, int maxVersion,
      Map<Integer, Class<? extends NGMigration>> migrations, MigrationDetails migrationDetail,
      MigrationProvider migrationProviderInstance, String serviceName, Microservice microservice) throws Exception {
    if (currentVersion < maxVersion) {
      if (isBackground) {
        runBackgroundMigrations(currentVersion, maxVersion, migrations, migrationDetail, migrationProviderInstance,
            serviceName, microservice);
      } else {
        runForegroundMigrations(
            currentVersion, maxVersion, migrations, migrationDetail, migrationProviderInstance, serviceName);
      }
    } else if (currentVersion > maxVersion) {
      // If the current version is bigger than the max version we are downgrading. Restore to the previous version
      log.info("[Migration] - {} : Rolling back {} version from {} to {}", serviceName,
          migrationDetail.getMigrationTypeName(), currentVersion, maxVersion);
      Update update =
          new Update().set(NGSchemaKeys.migrationDetails + "." + migrationDetail.getMigrationTypeName(), maxVersion);
      mongoTemplate.updateFirst(new Query(), update, migrationProviderInstance.getSchemaClass());
    } else {
      log.info("[Migration] - {} : NGSchema {} is up to date", serviceName, migrationDetail.getMigrationTypeName());
    }
  }

  private void runForegroundMigrations(int currentVersion, int maxVersion,
      Map<Integer, Class<? extends NGMigration>> migrations, MigrationDetails migrationDetail,
      MigrationProvider migrationProviderInstance, String serviceName) throws Exception {
    doMigration(false, currentVersion, maxVersion, migrations, migrationDetail.getMigrationTypeName(),
        migrationProviderInstance, serviceName);
  }

  private void runBackgroundMigrations(int currentVersion, int maxVersion,
      Map<Integer, Class<? extends NGMigration>> migrations, MigrationDetails migrationDetail,
      MigrationProvider migrationProviderInstance, String serviceName, Microservice microservice) {
    if (currentVersion < maxVersion) {
      executorService.submit(() -> {
        MigrationType migrationType = migrationDetail.getMigrationTypeName();
        try (AcquiredLock ignore = persistentLocker.acquireLock(
                 NGSchema.class, "Background-" + NG_SCHEMA_ID + microservice + migrationType, ofMinutes(120 + 1))) {
          timeLimiter.<Boolean>callWithTimeout(() -> {
            doMigration(
                true, currentVersion, maxVersion, migrations, migrationType, migrationProviderInstance, serviceName);
            return true;
          }, 2, TimeUnit.HOURS, true);
        } catch (Exception ex) {
          log.warn("Migration work", ex);
        }
      });
    }
  }

  @VisibleForTesting
  void doMigration(boolean isBackground, int currentVersion, int maxVersion,
      Map<Integer, Class<? extends NGMigration>> migrations, MigrationType migrationTypeName,
      MigrationProvider migrationProviderInstance, String serviceName) throws Exception {
    log.info("[Migration] - {} : Updating {} version from {} to {}", serviceName, migrationTypeName, currentVersion,
        maxVersion);

    for (int i = currentVersion + 1; i <= maxVersion; i++) {
      if (!migrations.containsKey(i)) {
        continue;
      }
      Class<? extends NGMigration> migration = migrations.get(i);
      log.info("[Migration] - {} : Migrating {} to version {} ...", serviceName, migrationTypeName, i);
      try {
        injector.getInstance(migration).migrate();
      } catch (Exception ex) {
        if (isBackground) {
          log.error("[Migration] - {} : Error while running migration {}", serviceName, migration.getSimpleName(), ex);
          break;
        } else {
          throw new Exception("Error while running migration", ex);
        }
      }

      Update update = new Update().set(NGSchemaKeys.migrationDetails + "." + migrationTypeName, i);
      mongoTemplate.updateFirst(new Query(), update, migrationProviderInstance.getSchemaClass());
    }

    log.info("[Migration] - {} : {} complete", serviceName, migrationTypeName);
  }
}
