package io.harness.ccm.views.graphql;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.anomalydetection.ADViewsQueryBuilder;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ADViewsQueryBuilderTest extends CategoryTest {
  @Inject @InjectMocks ADViewsQueryBuilder adViewsQueryBuilder;
  private QLCEViewTimeFilter endTimeFilter;
  private QLCEViewTimeFilter startTimeFilter;
  private static final String awsFilter = "awsService IS NOT NULL";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    startTimeFilter = QLCEViewTimeFilter.builder()
                          .field(QLCEViewFieldInput.builder()
                                     .fieldId(ViewsMetaDataFields.START_TIME.getFieldName())
                                     .fieldName(ViewsMetaDataFields.START_TIME.getFieldName())
                                     .identifier(ViewFieldIdentifier.COMMON)
                                     .build())
                          .operator(QLCEViewTimeFilterOperator.AFTER)
                          .value(Long.valueOf(0))
                          .build();

    endTimeFilter = QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId(ViewsMetaDataFields.START_TIME.getFieldName())
                                   .fieldName(ViewsMetaDataFields.START_TIME.getFieldName())
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .build())
                        .operator(QLCEViewTimeFilterOperator.BEFORE)
                        .value(Instant.now().toEpochMilli())
                        .build();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void testGetQueryAwsView() {
    List<QLCEViewField> awsFields = ViewFieldUtils.getAwsFields();
    final QLCEViewField awsService = awsFields.get(0);
    final QLCEViewField awsAccount = awsFields.get(1);

    List<ViewRule> viewRules = Arrays.asList(
        ViewRule.builder()
            .viewConditions(Arrays.asList(ViewIdCondition.builder()
                                              .viewField(ViewField.builder()
                                                             .fieldName(awsService.getFieldName())
                                                             .fieldId(awsService.getFieldId())
                                                             .identifier(ViewFieldIdentifier.AWS)
                                                             .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                             .build())
                                              .viewOperator(ViewIdOperator.IN)
                                              .values(Arrays.asList("service1"))
                                              .build()))
            .build());

    QLCEViewGroupBy groupBy = QLCEViewGroupBy.builder()
                                  .entityGroupBy(QLCEViewFieldInput.builder()
                                                     .fieldId(awsAccount.getFieldId())
                                                     .fieldName(awsAccount.getFieldName())
                                                     .identifier(ViewFieldIdentifier.AWS)
                                                     .identifierName(ViewFieldIdentifier.AWS.getDisplayName())
                                                     .build())
                                  .build();
    SelectQuery selectQuery =
        adViewsQueryBuilder.getQuery(viewRules, Collections.emptyList(), Arrays.asList(startTimeFilter, endTimeFilter),
            Collections.singletonList(groupBy), Collections.EMPTY_LIST, Collections.EMPTY_LIST, "TableName");
    assertThat(selectQuery.toString()).contains("GROUP BY awsUsageAccountId");
    assertThat(selectQuery.toString()).contains("((awsServicecode IN ('service1') )");
    assertThat(selectQuery.toString()).contains("SELECT awsUsageAccountId FROM TableName");

    // SELECT awsUsageAccountId FROM TableName WHERE ((awsServicecode IN ('service1') ) AND (startTime >=
    // '1970-01-01T00:00:00Z') AND (startTime <= '2021-03-08T14:09:51.751Z')) GROUP BY awsUsageAccountId
  }
}
