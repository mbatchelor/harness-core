package io.harness.repositories.instancesyncperpetualtask;

import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;

import com.google.inject.Inject;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.DX)
public class InstanceSyncPerpetualTaskRepositoryCustomImpl implements InstanceSyncPerpetualTaskRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public InstanceSyncPerpetualTaskInfo update(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), InstanceSyncPerpetualTaskInfo.class);
  }
}
