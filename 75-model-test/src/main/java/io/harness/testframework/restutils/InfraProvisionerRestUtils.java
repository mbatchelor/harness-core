package io.harness.testframework.restutils;

import com.google.inject.Singleton;

import io.harness.data.structure.EmptyPredicate;
import io.harness.rest.RestResponse;
import io.harness.testframework.framework.Setup;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import software.wings.beans.InfrastructureProvisioner;

import javax.ws.rs.core.GenericType;

@Singleton
public class InfraProvisionerRestUtils {
  public static InfrastructureProvisioner saveProvisioner(
      String appId, String bearerToken, InfrastructureProvisioner infrastructureProvisioner) throws Exception {
    GenericType<RestResponse<InfrastructureProvisioner>> provisioner =
        new GenericType<RestResponse<InfrastructureProvisioner>>() {};

    RestResponse<InfrastructureProvisioner> response = Setup.portal()
                                                           .auth()
                                                           .oauth2(bearerToken)
                                                           .queryParam("appId", appId)
                                                           .body(infrastructureProvisioner, ObjectMapperType.GSON)
                                                           .contentType(ContentType.JSON)
                                                           .post("/infrastructure-provisioners")
                                                           .as(provisioner.getType());
    if (response.getResource() == null) {
      throw new Exception(String.valueOf(response.getResponseMessages()));
    }
    return response.getResource();
  }

  public static void deleteProvisioner(String appId, String bearerToken, String provisionerId) throws Exception {
    RestResponse response = Setup.portal()
                                .auth()
                                .oauth2(bearerToken)
                                .queryParam("appId", appId)
                                .contentType(ContentType.JSON)
                                .delete("/infrastructure-provisioners/" + provisionerId)
                                .as(RestResponse.class);
    if (EmptyPredicate.isNotEmpty(response.getResponseMessages())) {
      throw new Exception(String.valueOf(response.getResponseMessages()));
    }
  }

  public static InfrastructureProvisioner getProvisioner(String appId, String bearerToken, String provisionerId)
      throws Exception {
    GenericType<RestResponse<InfrastructureProvisioner>> provisioner =
        new GenericType<RestResponse<InfrastructureProvisioner>>() {};

    RestResponse<InfrastructureProvisioner> response = Setup.portal()
                                                           .auth()
                                                           .oauth2(bearerToken)
                                                           .queryParam("appId", appId)
                                                           .contentType(ContentType.JSON)
                                                           .get("/infrastructure-provisioners/" + provisionerId)
                                                           .as(provisioner.getType());
    if (response.getResource() == null) {
      throw new Exception(String.valueOf(response.getResponseMessages()));
    }
    return response.getResource();
  }

  public static InfrastructureProvisioner updateProvisioner(String appId, String bearerToken,
      InfrastructureProvisioner infrastructureProvisioner, String provisionerid) throws Exception {
    GenericType<RestResponse<InfrastructureProvisioner>> provisioner =
        new GenericType<RestResponse<InfrastructureProvisioner>>() {};
    RestResponse<InfrastructureProvisioner> response = Setup.portal()
                                                           .auth()
                                                           .oauth2(bearerToken)
                                                           .queryParam("appId", appId)
                                                           .body(infrastructureProvisioner, ObjectMapperType.GSON)
                                                           .contentType(ContentType.JSON)
                                                           .put("/infrastructure-provisioners/" + provisionerid)
                                                           .as(provisioner.getType());
    if (response.getResource() == null) {
      throw new Exception(String.valueOf(response.getResponseMessages()));
    }
    return response.getResource();
  }
}
