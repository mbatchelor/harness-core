/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.service;

import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRequestDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;

public interface ArtifactoryResourceService {
  ArtifactoryResponseDTO getBuildDetails(
      IdentifierRef artifactoryConnectorRef, String imagePath, String orgIdentifier, String projectIdentifier);

  ArtifactoryResponseDTO getLabels(IdentifierRef artifactoryConnectorRef, String imagePath,
      ArtifactoryRequestDTO artifactoryRequestDTO, String orgIdentifier, String projectIdentifier);

  ArtifactoryBuildDetailsDTO getSuccessfulBuild(IdentifierRef artifactoryConnectorRef, String imagePath,
      ArtifactoryRequestDTO artifactoryRequestDTO, String orgIdentifier, String projectIdentifier);

  boolean validateArtifactServer(IdentifierRef artifactoryConnectorRef, String orgIdentifier, String projectIdentifier);

  // In case of docker source here is docker image.
  boolean validateArtifactSource(
      String imagePath, IdentifierRef artifactoryConnectorRef, String orgIdentifier, String projectIdentifier);
}
