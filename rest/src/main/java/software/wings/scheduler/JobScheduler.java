package software.wings.scheduler;

import com.google.inject.Injector;

import software.wings.app.MainConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by anubhaw on 10/21/16.
 */
@Singleton
public class JobScheduler extends AbstractQuartzScheduler {
  /**
   * Instantiates a new Cron scheduler.
   *
   * @param injector      the injector
   * @param configuration the configuration
   */
  @Inject
  public JobScheduler(Injector injector, MainConfiguration configuration) {
    super(injector, configuration);
  }
}
