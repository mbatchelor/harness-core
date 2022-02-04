/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.accountsetting.resources;

import static io.harness.NGCommonEntityConstants.*;

import io.harness.NGCommonEntityConstants;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.AccountSettingsDTO;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.ng.core.accountsetting.services.NGAccountSettingService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/account-setting")
@Path("/account-setting")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html", "text/plain"})
@Tag(name = "AccountSetting", description = "This contains APIs related to Account Settings as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class AccountSettingResource {
  NGAccountSettingService accountSettingService;

  @GET
  @ApiOperation(value = "Gets account setting", nickname = "getAccountSetting")
  public ResponseDTO<AccountSettingResponseDTO> getAccountSetting(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(TYPE_KEY) AccountSettingType type) {
    return ResponseDTO.newResponse(accountSettingService.get(accountId, orgIdentifier, projectIdentifier, type));
  }

  @GET
  @Path("/list")
  @ApiOperation(value = "List account setting", nickname = "listAccountSetting")
  public ResponseDTO<List<AccountSettingsDTO>> listSettings(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(TYPE_KEY) AccountSettingType type) {
    return ResponseDTO.newResponse(accountSettingService.list(accountId, orgIdentifier, projectIdentifier, type));
  }

  @POST
  @ApiOperation(value = "Create a account setting", nickname = "createAccountSetting")
  @Operation(operationId = "createAccountSetting", summary = "Creates a account setting",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created account setting")
      })
  public ResponseDTO<AccountSettingResponseDTO>
  create(@RequestBody(required = true, description = "Details of the ACcountSetting to create") @Valid
         @NotNull AccountSettingsDTO accountSettingsDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier

  ) {
    return ResponseDTO.newResponse(accountSettingService.create(accountSettingsDTO, accountIdentifier));
  }

  @PUT
  @ApiOperation(value = "Update a account setting", nickname = "updateAccountSetting")
  @Operation(operationId = "updateAccountSetting", summary = "Creates a account setting",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created account setting")
      })
  public ResponseDTO<AccountSettingResponseDTO>
  update(@RequestBody(required = true, description = "Details of the ACcountSetting to create") @Valid
         @NotNull AccountSettingsDTO accountSettingsDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier) {
    return ResponseDTO.newResponse(accountSettingService.update(accountSettingsDTO, accountIdentifier));
  }
}
