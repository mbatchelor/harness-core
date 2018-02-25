package software.wings.integration.migration.legacy;

import static software.wings.dl.PageRequest.PageRequestBuilder.aPageRequest;
import static software.wings.dl.PageRequest.UNLIMITED;

import com.google.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SearchFilter;
import software.wings.beans.SettingAttribute;
import software.wings.dl.PageRequest;
import software.wings.dl.WingsPersistence;
import software.wings.rules.Integration;

import java.util.List;

/**
 * Script to list AWS keys.
 *
 * @author brett on 10/1/17
 */
@Integration
@Ignore
public class AwsListSecretKeysUtil extends WingsBaseTest {
  @Inject private WingsPersistence wingsPersistence;

  @Test
  public void listAwsKeys() throws InterruptedException {
    PageRequest<SettingAttribute> pageRequest = aPageRequest()
                                                    .addFilter("category", SearchFilter.Operator.EQ, "CLOUD_PROVIDER")
                                                    .addFilter("value.type", SearchFilter.Operator.EQ, "AWS")
                                                    .withLimit(UNLIMITED)
                                                    .build();

    List<SettingAttribute> settingAttributes =
        wingsPersistence.query(SettingAttribute.class, pageRequest).getResponse();
    //    List<SettingAttribute> settingAttributes =
    //    wingsPersistence.createQuery(SettingAttribute.class).field("category")
    //        .equal("CLOUD_PROVIDER").field("value.type").equal("AWS").asList();
    System.out.println("found " + settingAttributes.size() + " records");
    for (SettingAttribute settingAttribute : settingAttributes) {
      AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
      String accessKey = awsConfig.getAccessKey();
      String secretKey = new String(awsConfig.getSecretKey());
      System.out.println(settingAttribute.getUuid() + " - " + accessKey + ": " + secretKey);
      //      wingsPersistence.save(SettingAttribute)
    }

    System.out.println("Complete.");
  }
}
