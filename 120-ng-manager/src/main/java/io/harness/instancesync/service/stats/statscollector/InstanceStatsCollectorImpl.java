package io.harness.instancesync.service.stats.statscollector;

import io.harness.instancesync.dto.Instance;
import io.harness.instancesync.helper.SnapshotTimeProvider;
import io.harness.instancesync.repository.instancesyncperpetualtask.InstanceRepository;
import io.harness.instancesync.service.instancestats.InstanceStatsService;
import io.harness.instancesync.service.stats.usagemetrics.eventpublisher.UsageMetricsEventPublisher;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class InstanceStatsCollectorImpl implements StatsCollector {
  private static final int SYNC_INTERVAL_MINUTES = 10;
  private static final long SYNC_INTERVAL = TimeUnit.MINUTES.toMinutes(SYNC_INTERVAL_MINUTES);

  private InstanceStatsService instanceStatsService;
  private InstanceRepository instanceRepository;
  private UsageMetricsEventPublisher usageMetricsEventPublisher;

  @Override
  public boolean createStats(String accountId) {
    Instant lastSnapshot = instanceStatsService.getLastSnapshotTime(accountId);
    if (null == lastSnapshot) {
      return createStats(accountId, alignedWithMinute(Instant.now(), SYNC_INTERVAL_MINUTES));
    }

    SnapshotTimeProvider snapshotTimeProvider = new SnapshotTimeProvider(lastSnapshot, SYNC_INTERVAL);
    boolean ranAtLeastOnce = false;
    while (snapshotTimeProvider.hasNext()) {
      Instant nextTs = snapshotTimeProvider.next();
      if (nextTs == null) {
        throw new IllegalStateException("nextTs is null even though hasNext() returned true. Shouldn't be possible");
      }
      boolean success = createStats(accountId, nextTs);
      ranAtLeastOnce = ranAtLeastOnce || success;
    }

    return ranAtLeastOnce;
  }

  // ------------------------ PRIVATE METHODS -----------------------------

  private Instant alignedWithMinute(Instant instant, int minuteToTruncateTo) {
    if (LocalDateTime.ofInstant(instant, ZoneOffset.UTC).getMinute() % minuteToTruncateTo == 0) {
      return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    Instant value = instant.truncatedTo(ChronoUnit.HOURS);
    while (!value.plus(minuteToTruncateTo, ChronoUnit.MINUTES).isAfter(instant)) {
      value = value.plus(minuteToTruncateTo, ChronoUnit.MINUTES);
    }

    return value;
  }

  private boolean createStats(String accountId, Instant instant) {
    List<Instance> instances = null;
    try {
      instances = instanceRepository.getActiveInstancesByAccount(accountId, instant.toEpochMilli());
      log.info("Fetched instances. Count: {}, Account: {}, Time: {}", instances.size(), accountId, instant);

      usageMetricsEventPublisher.publishInstanceStatsTimeSeries(accountId, instant.toEpochMilli(), instances);
      return true;
    } catch (Exception e) {
      // TODO handle exception gracefully
      return false;
    }
  }
}
