package software.wings.resources;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE_SCOPE;

import com.google.inject.Inject;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.harness.delegate.beans.TaskSelectorMap;
import io.harness.rest.RestResponse;
import io.harness.service.intfc.DelegateTaskSelectorMapService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;

import java.util.List;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("delegate-task-selector-map")
@Path("/delegate-task-selector-map")
@Produces("application/json")
@Scope(DELEGATE_SCOPE)
@AuthRule(permissionType = LOGGED_IN)
// TODO: we should use this auth rule: @AuthRule(permissionType = MANAGE_TASK_SELECTORS)
public class DelegateTaskSelectorMapResource {
  private DelegateTaskSelectorMapService selectorMapService;

  @Inject
  public DelegateTaskSelectorMapResource(DelegateTaskSelectorMapService selectorMapService) {
    this.selectorMapService = selectorMapService;
  }

  @GET
  @ApiImplicitParams(
      { @ApiImplicitParam(name = "accountId", required = true, dataType = "string", paramType = "query") })
  @Timed
  @ExceptionMetered
  public RestResponse<List<TaskSelectorMap>>
  list() {
    return new RestResponse<>(selectorMapService.list());
  }

  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<TaskSelectorMap> add(
      @QueryParam("accountId") @NotEmpty String accountId, TaskSelectorMap taskSelectorMap) {
    taskSelectorMap.setAccountId(accountId);
    return new RestResponse<>(selectorMapService.add(taskSelectorMap));
  }

  @PUT
  @Path("/{taskSelectorMapId}")
  @Timed
  @ExceptionMetered
  public RestResponse<TaskSelectorMap> update(@PathParam("taskSelectorMapId") @NotEmpty String taskSelectorMapId,
      @QueryParam("accountId") @NotEmpty String accountId, TaskSelectorMap taskSelectorMap) {
    taskSelectorMap.setAccountId(accountId);
    taskSelectorMap.setUuid(taskSelectorMapId);
    return new RestResponse<>(selectorMapService.update(taskSelectorMap));
  }

  @POST
  @Path("/{taskSelectorMapId}/task-selectors")
  @Timed
  @ExceptionMetered
  public RestResponse<TaskSelectorMap> addTaskSelector(
      @PathParam("taskSelectorMapId") @NotEmpty String taskSelectorMapId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("selector") @NotEmpty String taskSelector) {
    return new RestResponse<>(selectorMapService.addTaskSelector(accountId, taskSelectorMapId, taskSelector));
  }

  @DELETE
  @Path("/{taskSelectorMapId}/task-selectors")
  @Timed
  @ExceptionMetered
  public RestResponse<TaskSelectorMap> deleteTaskSelector(
      @PathParam("taskSelectorMapId") @NotEmpty String taskSelectorMapId,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("selector") @NotEmpty String taskSelector) {
    return new RestResponse<>(selectorMapService.removeTaskSelector(accountId, taskSelectorMapId, taskSelector));
  }
}
