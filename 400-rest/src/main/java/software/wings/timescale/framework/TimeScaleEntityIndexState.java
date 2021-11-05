package software.wings.timescale.framework;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.PersistentEntity;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDC)
@Value
@Entity(value = "timeScaleEntitiesIndexState", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "TimeScaleEntityIndexStateKeys")
@Slf4j
public class TimeScaleEntityIndexState implements PersistentEntity {
  @Id private String entityClass;
  @Getter @Setter private long lastUpdated;
  @Getter @Setter private List<String> alreadyMigratedAccountIds;
  @Getter @Setter private List<String> toMigrateAccountIds;
}
