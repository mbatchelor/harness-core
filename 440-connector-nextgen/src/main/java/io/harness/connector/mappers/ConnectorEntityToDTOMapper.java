/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers;

import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;

public interface ConnectorEntityToDTOMapper<D extends ConnectorConfigDTO, B extends Connector> {
  D createConnectorDTO(B connector);
}
