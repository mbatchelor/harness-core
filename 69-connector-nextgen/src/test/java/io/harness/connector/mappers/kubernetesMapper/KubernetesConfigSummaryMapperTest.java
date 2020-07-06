package io.harness.connector.mappers.kubernetesMapper;

import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorsBaseTest;
import io.harness.connector.apis.dto.k8connector.KubernetesConfigSummaryDTO;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterConfig;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesClusterDetails;
import io.harness.connector.entities.embedded.kubernetescluster.KubernetesDelegateDetails;
import io.harness.connector.entities.embedded.kubernetescluster.UserNamePasswordK8;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class KubernetesConfigSummaryMapperTest extends ConnectorsBaseTest {
  @Inject @InjectMocks KubernetesConfigSummaryMapper kubernetesConfigSummaryMapper;

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createKubernetesConfigSummaryDTOForDelegateCredentials() {
    String delegateName = "testDeleagete";
    KubernetesDelegateDetails delegateCredential =
        KubernetesDelegateDetails.builder().delegateName(delegateName).build();
    KubernetesClusterConfig kubernetesClusterConfig =
        KubernetesClusterConfig.builder().credentialType(INHERIT_FROM_DELEGATE).credential(delegateCredential).build();
    KubernetesConfigSummaryDTO kubernetesConfigSummaryDTO =
        kubernetesConfigSummaryMapper.createKubernetesConfigSummaryDTO(kubernetesClusterConfig);
    assertThat(kubernetesConfigSummaryDTO).isNotNull();
    assertThat(kubernetesConfigSummaryDTO.getDelegateName()).isEqualTo(delegateName);
    assertThat(kubernetesConfigSummaryDTO.getMasterURL()).isBlank();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void createKubernetesConfigSummaryDTOForManualCredentials() {
    String masterURL = "masterURL";
    String userName = "userName";
    String password = "password";
    String cacert = "cacert";
    UserNamePasswordK8 userNamePasswordK8 =
        UserNamePasswordK8.builder().userName(userName).password(password).cacert(cacert).build();
    KubernetesClusterDetails kubernetesClusterDetails = KubernetesClusterDetails.builder()
                                                            .masterUrl(masterURL)
                                                            .authType(KubernetesAuthType.USER_PASSWORD)
                                                            .auth(userNamePasswordK8)
                                                            .build();
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                          .credentialType(MANUAL_CREDENTIALS)
                                                          .credential(kubernetesClusterDetails)
                                                          .build();

    KubernetesConfigSummaryDTO kubernetesConfigSummaryDTO =
        kubernetesConfigSummaryMapper.createKubernetesConfigSummaryDTO(kubernetesClusterConfig);
    assertThat(kubernetesConfigSummaryDTO).isNotNull();
    assertThat(kubernetesConfigSummaryDTO.getMasterURL()).isEqualTo(masterURL);
    assertThat(kubernetesConfigSummaryDTO.getDelegateName()).isBlank();
  }
}