package io.harness.delegate.task.gcp.request;

import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class GcpValidationRequest extends GcpRequest implements TaskParameters {
  @Builder
  public GcpValidationRequest(String delegateSelector, List<EncryptedDataDetail> encryptedDataDetailList,
      GcpManualDetailsDTO gcpManualDetailsDTO) {
    super(delegateSelector, RequestType.VALIDATE, encryptedDataDetailList, gcpManualDetailsDTO);
  }
}
