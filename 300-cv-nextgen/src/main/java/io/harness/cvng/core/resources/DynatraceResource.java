package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.cvng.core.beans.MetricPackValidationResponse;
import io.harness.cvng.core.beans.dynatrace.DynatraceServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DynatraceService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api("dynatrace")
@Path("/dynatrace")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class DynatraceResource {
  @Inject() private DynatraceService dynatraceService;

  @GET
  @Path("/services")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all dynatrace services", nickname = "getDynatraceServices")
  public ResponseDTO<List<DynatraceServiceDTO>> getDynatraceServices(@NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier, @QueryParam("filter") String filter,
      @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(dynatraceService.getAllServices(ProjectParams.builder()
                                                                       .projectIdentifier(projectIdentifier)
                                                                       .orgIdentifier(orgIdentifier)
                                                                       .accountIdentifier(accountId)
                                                                       .build(),
        connectorIdentifier, filter, tracingId));
  }

  @GET
  @Path("/service-details")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get dynatrace service details", nickname = "getDynatraceServiceDetails")
  public ResponseDTO<DynatraceServiceDTO> getDynatraceServiceDetails(@NotNull @QueryParam("accountId") String accountId,
      @NotNull @QueryParam("connectorIdentifier") final String connectorIdentifier,
      @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("serviceId") @NotNull String serviceId, @NotNull @QueryParam("tracingId") String tracingId) {
    return ResponseDTO.newResponse(dynatraceService.getServiceDetails(ProjectParams.builder()
                                                                          .projectIdentifier(projectIdentifier)
                                                                          .orgIdentifier(orgIdentifier)
                                                                          .accountIdentifier(accountId)
                                                                          .build(),
        connectorIdentifier, serviceId, tracingId));
  }

  @POST
  @Path("/metric-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get metric data for given metric packs", nickname = "getDynatraceMetricData")
  public ResponseDTO<Set<MetricPackValidationResponse>> getDynatraceMetricData(
      @QueryParam("accountId") @NotNull String accountId, @QueryParam("orgIdentifier") @NotNull String orgIdentifier,
      @QueryParam("projectIdentifier") @NotNull String projectIdentifier,
      @QueryParam("connectorIdentifier") @NotNull String connectorIdentifier,
      @QueryParam("serviceId") @NotNull String serviceId, @QueryParam("tracingId") @NotNull String tracingId,
      @NotNull @Valid @Body List<MetricPackDTO> metricPacks) {
    return ResponseDTO.newResponse(dynatraceService.validateData(ProjectParams.builder()
                                                                     .projectIdentifier(projectIdentifier)
                                                                     .orgIdentifier(orgIdentifier)
                                                                     .accountIdentifier(accountId)
                                                                     .build(),
        connectorIdentifier, serviceId, metricPacks, tracingId));
  }
}
