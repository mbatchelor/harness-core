package io.harness.notification.channeldetails;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.NotificationRequest;
import io.harness.Team;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@EqualsAndHashCode(callSuper = true)
public class EmailChannel extends NotificationChannel {
  List<String> recipients;

  @Builder
  public EmailChannel(String accountId, List<String> userGroupIds, List<NotificationRequest.UserGroup> userGroups,
      String templateId, Map<String, String> templateData, Team team, List<String> recipients) {
    super(accountId, userGroupIds, userGroups, templateId, templateData, team);
    this.recipients = recipients;
  }

  @Override
  public NotificationRequest buildNotificationRequest() {
    NotificationRequest.Builder builder = NotificationRequest.newBuilder();
    String notificationId = generateUuid();
    return builder.setId(notificationId)
        .setAccountId(accountId)
        .setTeam(team)
        .setEmail(builder.getEmailBuilder()
                      .addAllEmailIds(recipients)
                      .setTemplateId(templateId)
                      .putAllTemplateData(templateData)
                      .addAllUserGroupIds(userGroupIds)
                      .addAllUserGroup(userGroups))
        .build();
  }
}
