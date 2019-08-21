package software.wings.service.impl.yaml.handler.infraDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import com.google.inject.Inject;

import io.harness.category.element.UnitTests;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.beans.InfrastructureType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.Yaml;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsEcsInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsInstanceInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AwsLambdaInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureInstanceInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.AzureKubernetesServiceYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.CodeDeployInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.DirectKubernetesInfrastructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.GoogleKubernetesEngineYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.InfrastructureDefinitionYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PcfInfraStructureYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PhysicalInfraWinrmYamlHandler;
import software.wings.service.impl.yaml.handler.InfraDefinition.PhysicalInfraYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.yaml.handler.BaseYamlHandlerTest;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;

public class InfrastructureDefinitionYamlHandlerTest extends BaseYamlHandlerTest {
  @Mock private YamlHelper mockYamlHelper;
  @Mock private SettingsService mockSettingsService;
  @Mock private YamlHandlerFactory mockYamlHandlerFactory;
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;

  @InjectMocks @Inject private InfrastructureDefinitionYamlHandler handler;
  @InjectMocks @Inject private AwsLambdaInfrastructureYamlHandler awsLambdaInfrastructureYamlHandler;
  @InjectMocks @Inject private GoogleKubernetesEngineYamlHandler googleKubernetesEngineYamlHandler;
  @InjectMocks @Inject private AzureKubernetesServiceYamlHandler azureKubernetesServiceYamlHandler;
  @InjectMocks @Inject private AzureInstanceInfrastructureYamlHandler azureInstanceInfrastructureYamlHandler;
  @InjectMocks @Inject private DirectKubernetesInfrastructureYamlHandler directKubernetesInfrastructureYamlHandler;
  @InjectMocks @Inject private AwsInstanceInfrastructureYamlHandler awsInstanceInfrastructureYamlHandler;
  @InjectMocks @Inject private CodeDeployInfrastructureYamlHandler codeDeployInfrastructureYamlHandler;
  @InjectMocks @Inject private AwsEcsInfrastructureYamlHandler awsEcsInfrastructureYamlHandler;
  @InjectMocks @Inject private PcfInfraStructureYamlHandler pcfInfraStructureYamlHandler;
  @InjectMocks @Inject private PhysicalInfraYamlHandler physicalInfraYamlHandler;
  @InjectMocks @Inject private PhysicalInfraWinrmYamlHandler physicalInfraWinrmYamlHandler;

  private final String yamlFilePath = "Setup/Applications/APP_NAME/Environments/"
      + "ENV_NAME/Infrastructure Definitions/infra-def.yaml";
  private final String resourcePath = "./infrastructureDefinitions";

  @UtilityClass
  private static class validYamlInfraStructureFiles {
    // Make sure that CloudProviderName is TEST_CLOUD_PROVIDER and InfraDefinition name is
    // infra-def in yaml files
    private static final String AWS_ECS = "aws_ecs.yaml";
    private static final String AWS_ECS_PROVISIONER = "aws_ecs_provisioner.yaml";
    private static final String AWS_LAMBDA = "aws_lambda.yaml";
    private static final String AWS_LAMBDA_PROVISIONER = "aws_lambda_provisioner.yaml";
    private static final String GCP_KUBERNETES = "gcp_kubernetes.yaml";
    private static final String AZURE_KUBERNETES = "azure_kubernetes.yaml";
    private static final String AZURE_INSTANCE = "azure_instance.yaml";
    private static final String DIRECT_KUBERNETES = "direct_kubernetes.yaml";
    private static final String AWS_INSTANCE = "aws_instance.yaml";
    private static final String AWS_INSTANCE_PROVISIONER = "aws_instance_provisioner.yaml";
    private static final String AWS_CODEDEPLOY = "aws_codedeploy.yaml";
    private static final String GCP_KUBERNETES_PROVISIONER = "gcp_kubernetes_provisioner.yaml";
    private static final String PCF = "pcf.yaml";
    private static final String PHYSICAL_INFRA = "physical_infra.yaml";
    private static final String PHYSICAL_INFRA_WINRM = "physical_infra_winrm.yaml";
    private static final String PHYSICAL_INFRA_PROVISIONER = "physicalInfra_provisioner.yaml";
  }
  private ArgumentCaptor<InfrastructureDefinition> captor = ArgumentCaptor.forClass(InfrastructureDefinition.class);

  @Before
  public void setup() {
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("TEST_CLOUD_PROVIDER").build();
    Service service = Service.builder().name("httpd").uuid(SERVICE_ID).build();
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(anyString());
    doReturn(APP_ID).when(mockYamlHelper).getAppId(anyString(), anyString());
    doReturn(ENV_ID).when(mockYamlHelper).getEnvironmentId(anyString(), anyString());
    doReturn(settingAttribute).when(mockSettingsService).getSettingAttributeByName(anyString(), anyString());
    doReturn(settingAttribute).when(mockSettingsService).get(anyString());
    doReturn("infra-def").when(mockYamlHelper).extractEntityNameFromYamlPath(anyString(), anyString(), anyString());
    doReturn(service).when(serviceResourceService).get(anyString(), anyString());
    doReturn(service).when(serviceResourceService).getServiceByName(anyString(), anyString());
    doReturn(Optional.of(anApplication().uuid(APP_ID).build()))
        .when(mockYamlHelper)
        .getApplicationIfPresent(anyString(), anyString());
    doReturn(Optional.of(anEnvironment().uuid(ENV_ID).build()))
        .when(mockYamlHelper)
        .getEnvIfPresent(anyString(), anyString());
  }

  @Test
  @Category(UnitTests.class)
  public void testCRUDAndGet_AWS_LAMBDA() throws IOException {
    doReturn(awsLambdaInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AWS_LAMBDA, InfrastructureType.AWS_LAMBDA, DeploymentType.AWS_LAMBDA,
        CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_LAMBDA_PROVISIONER, InfrastructureType.AWS_LAMBDA,
        DeploymentType.AWS_LAMBDA, CloudProviderType.AWS);
  }

  @Test
  @Category(UnitTests.class)
  public void testCRUDAndGet_AWS_ECS() throws IOException {
    doReturn(awsEcsInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(
        validYamlInfraStructureFiles.AWS_ECS, InfrastructureType.AWS_ECS, DeploymentType.ECS, CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_ECS_PROVISIONER, InfrastructureType.AWS_ECS, DeploymentType.ECS,
        CloudProviderType.AWS);
  }

  @Test
  @Category(UnitTests.class)
  public void testCRUDAndGet_GCP_KUBERNETES() throws IOException {
    doReturn(googleKubernetesEngineYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.GCP_KUBERNETES, InfrastructureType.GCP_KUBERNETES_ENGINE,
        DeploymentType.KUBERNETES, CloudProviderType.GCP);
    testCRUD(validYamlInfraStructureFiles.GCP_KUBERNETES_PROVISIONER, InfrastructureType.GCP_KUBERNETES_ENGINE,
        DeploymentType.KUBERNETES, CloudProviderType.GCP);
  }
  @Test
  @Category(UnitTests.class)
  public void testCRUDAndGet_AZURE_KUBERNETES() throws IOException {
    doReturn(azureKubernetesServiceYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AZURE_KUBERNETES, InfrastructureType.AZURE_KUBERNETES,
        DeploymentType.KUBERNETES, CloudProviderType.AZURE);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_AZURE_SSH() throws IOException {
    doReturn(azureInstanceInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.AZURE_INSTANCE, InfrastructureType.AZURE_SSH, DeploymentType.SSH,
        CloudProviderType.AZURE);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_DIRECT_KUBERNETES() throws IOException {
    doReturn(directKubernetesInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.DIRECT_KUBERNETES, InfrastructureType.DIRECT_KUBERNETES,
        DeploymentType.KUBERNETES, CloudProviderType.KUBERNETES_CLUSTER);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_AWS_INSTANCE() throws IOException {
    doReturn(awsInstanceInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), anyString());
    testCRUD(validYamlInfraStructureFiles.AWS_INSTANCE, InfrastructureType.AWS_INSTANCE, DeploymentType.WINRM,
        CloudProviderType.AWS);
    testCRUD(validYamlInfraStructureFiles.AWS_INSTANCE_PROVISIONER, InfrastructureType.AWS_INSTANCE, DeploymentType.SSH,
        CloudProviderType.AWS);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_AWS_CODEDEPLOY() throws IOException {
    doReturn(codeDeployInfrastructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), anyString());
    testCRUD(validYamlInfraStructureFiles.AWS_CODEDEPLOY, InfrastructureType.CODE_DEPLOY, DeploymentType.AWS_CODEDEPLOY,
        CloudProviderType.AWS);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_PCF() throws IOException {
    doReturn(pcfInfraStructureYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.PCF, InfrastructureType.PCF_INFRASTRUCTURE, DeploymentType.PCF,
        CloudProviderType.PCF);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_PhysicalInfra() throws IOException {
    doReturn(physicalInfraYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.PHYSICAL_INFRA, InfrastructureType.PHYSICAL_INFRA, DeploymentType.SSH,
        CloudProviderType.PHYSICAL_DATA_CENTER);
    testCRUD(validYamlInfraStructureFiles.PHYSICAL_INFRA_PROVISIONER, InfrastructureType.PHYSICAL_INFRA,
        DeploymentType.SSH, CloudProviderType.PHYSICAL_DATA_CENTER);
  }

  @Test
  @Category(UnitTests.class)
  public void TestCRUDAndGet_PhysicalInfraWinrm() throws IOException {
    doReturn(physicalInfraWinrmYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(any(), any());
    testCRUD(validYamlInfraStructureFiles.PHYSICAL_INFRA_WINRM, InfrastructureType.PHYSICAL_INFRA_WINRM,
        DeploymentType.WINRM, CloudProviderType.PHYSICAL_DATA_CENTER);
  }

  private void testCRUD(String yamlFileName, String cloudProviderInfrastructureType, DeploymentType deploymentType,
      CloudProviderType cloudProviderType) throws IOException {
    doReturn(null).when(mockYamlHelper).getInfraDefinitionByAppIdYamlPath(anyString(), anyString(), anyString());
    File yamlFile = null;
    try {
      yamlFile =
          new File(getClass().getClassLoader().getResource(resourcePath + PATH_DELIMITER + yamlFileName).toURI());
    } catch (URISyntaxException e) {
      fail("Unable to find yaml file " + yamlFileName);
    }
    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<Yaml> changeContext = getChangeContext(yamlString);
    Yaml yaml = (Yaml) getYaml(yamlString, Yaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));

    verify(infrastructureDefinitionService).save(captor.capture(), any(boolean.class));
    InfrastructureDefinition savedDefinition = captor.getValue();

    assertThat(savedDefinition).isNotNull();
    assertEquals(savedDefinition.getCloudProviderType(), cloudProviderType);
    assertEquals(savedDefinition.getDeploymentType(), deploymentType);
    assertEquals(savedDefinition.getInfrastructure().getInfrastructureType(), cloudProviderInfrastructureType);

    yaml = handler.toYaml(savedDefinition, APP_ID);

    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertEquals(yamlString, yamlContent);

    doReturn(savedDefinition)
        .when(infrastructureDefinitionService)
        .getInfraDefByName(anyString(), anyString(), anyString());

    InfrastructureDefinition retrievedDefinition = handler.get(ACCOUNT_ID, yamlFilePath);

    assertThat(retrievedDefinition).isNotNull();
    assertEquals(savedDefinition.getUuid(), retrievedDefinition.getUuid());

    doReturn(savedDefinition)
        .when(mockYamlHelper)
        .getInfraDefinitionByAppIdYamlPath(anyString(), anyString(), anyString());

    handler.delete(changeContext);

    verify(infrastructureDefinitionService).deleteByYamlGit(anyString(), anyString());

    reset(infrastructureDefinitionService);
  }

  private ChangeContext<Yaml> getChangeContext(String validYamlContent) {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.INFRA_DEFINITION);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }
}
