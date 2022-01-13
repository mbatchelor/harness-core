/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.artifactoryconnector;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(CDC)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ArtifactoryUsernamePasswordAuthDTO.class, name = ArtifactoryConstants.USERNAME_PASSWORD)
})
@ApiModel("ArtifactoryAuthCredentials")
@Schema(name = "ArtifactoryAuthCredentials",
    description = "This entity contains the details of credentials for Artifactory Authentication")
public interface ArtifactoryAuthCredentialsDTO extends DecryptableEntity {}
