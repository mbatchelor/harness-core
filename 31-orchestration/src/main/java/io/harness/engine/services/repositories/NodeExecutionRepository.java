package io.harness.engine.services.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.execution.NodeExecution;
import io.harness.execution.status.Status;
import org.springframework.data.repository.CrudRepository;

import java.util.EnumSet;
import java.util.List;

@HarnessRepo
public interface NodeExecutionRepository extends CrudRepository<NodeExecution, String> {
  List<NodeExecution> findByPlanExecutionId(String planExecutionId);

  List<NodeExecution> findByPlanExecutionIdAndOldRetry(String planExecutionId, Boolean oldRetry);

  List<NodeExecution> findByPlanExecutionIdAndParentIdOrderByCreatedAtDesc(String planExecutionId, String parentId);

  List<NodeExecution> findByPlanExecutionIdAndStatus(String planExecutionId, Status status);

  List<NodeExecution> findByPlanExecutionIdAndStatusIn(String planExecutionId, EnumSet<Status> statuses);

  List<NodeExecution> findByPlanExecutionIdAndParentIdInAndStatusIn(
      String planExecutionId, List<String> parentIds, EnumSet<Status> statuses);
}
