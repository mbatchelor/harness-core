package software.wings.resources;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static software.wings.security.PermissionAttribute.Action.EXECUTE;
import static software.wings.security.PermissionAttribute.Action.READ;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import com.google.inject.Inject;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import io.swagger.annotations.Api;
import org.hibernate.validator.constraints.NotEmpty;
import software.wings.beans.Preference;
import software.wings.beans.RestResponse;
import software.wings.dl.PageRequest;
import software.wings.dl.PageResponse;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.PreferenceService;

import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * The Class Preference Resource.
 */
@Api("preference")
@Path("/preference")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
public class PreferenceResource {
  private PreferenceService prefService;

  @Inject
  public PreferenceResource(PreferenceService prefService) {
    this.prefService = prefService;
  }

  /**
   * List the preferences
   *
   * @param accountId         the account id
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN, action = READ)
  public RestResponse<PageResponse<Preference>> listPreferences(
      @QueryParam("accountId") @NotEmpty String accountId, @BeanParam PageRequest<Preference> pageRequest) {
    return new RestResponse<>(prefService.list(pageRequest));
  }

  /**
   * Get the preference
   *
   * @param accountId         the account id
   * @param userId            the user id
   * @param preferenceId      the preference id
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @Path("{preferenceId}")
  @AuthRule(permissionType = LOGGED_IN, action = READ)
  public RestResponse<Preference> getPreference(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("userId") @NotEmpty String userId, @PathParam("preferenceId") String preferenceId) {
    return new RestResponse<>(prefService.get(accountId, userId, preferenceId));
  }

  /**
   * Create the preference
   *
   * @param accountId         the account id
   * @param userId            the user id
   * @param preference        the preference
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN, action = EXECUTE)
  public RestResponse<Preference> savePreference(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("userId") @NotEmpty String userId, Preference preference) {
    preference.setUserId(userId);
    preference.setAccountId(accountId);
    return new RestResponse<>(prefService.save(accountId, userId, preference));
  }

  /**
   * Update the preference
   *
   * @param accountId         the account id
   * @param userId            the user id
   * @param preferenceId      the preference id
   * @param preference        the preference
   * @return the rest response
   */
  @PUT
  @Timed
  @ExceptionMetered
  @Path("{preferenceId}")
  @AuthRule(permissionType = LOGGED_IN, action = EXECUTE)
  public RestResponse<Preference> updatePreference(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("userId") @NotEmpty String userId, @PathParam("preferenceId") String preferenceId,
      Preference preference) {
    return new RestResponse<>(prefService.update(accountId, userId, preferenceId, preference));
  }

  /**
   * Delete the preference
   *
   * @param accountId         the account id
   * @param userId            the user id
   * @param preferenceId      the preference id
   * @return the rest response
   */
  @DELETE
  @Path("{preferenceId}")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN, action = EXECUTE)
  public RestResponse<Void> deletePreference(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("userId") @NotEmpty String userId, @PathParam("preferenceId") String preferenceId) {
    prefService.delete(accountId, userId, preferenceId);
    return new RestResponse<>();
  }
}
