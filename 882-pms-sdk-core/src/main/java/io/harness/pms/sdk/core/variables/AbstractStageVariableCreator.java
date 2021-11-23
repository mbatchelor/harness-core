package io.harness.pms.sdk.core.variables;

import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractStageVariableCreator extends ChildrenVariableCreator {
  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    YamlNode node = config.getNode();
    String stageUUID = node.getUuid();
    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
    yamlPropertiesMap.put(stageUUID,
        YamlProperties.newBuilder()
            .setLocalName(YAMLFieldNameConstants.STAGE)
            .setFqn(YamlUtils.getFullyQualifiedName(node))
            .build());
    addVariablesForStage(yamlPropertiesMap, node);
    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  private void addVariablesForStage(Map<String, YamlProperties> yamlPropertiesMap, YamlNode yamlNode) {
    YamlField nameField = yamlNode.getField(YAMLFieldNameConstants.NAME);
    if (nameField != null) {
      String nameFQN = YamlUtils.getFullyQualifiedName(nameField.getNode());
      String nameLocal = YamlUtils.getQualifiedNameTillGivenField(nameField.getNode(), YAMLFieldNameConstants.STAGE);
      yamlPropertiesMap.put(nameField.getNode().getCurrJsonNode().textValue(),
          YamlProperties.newBuilder().setLocalName(getStageLocalName(nameLocal)).setFqn(nameFQN).build());
    }
    YamlField descriptionField = yamlNode.getField(YAMLFieldNameConstants.DESCRIPTION);
    if (descriptionField != null) {
      String descriptionFQN = YamlUtils.getFullyQualifiedName(descriptionField.getNode());
      String descriptionLocal =
          YamlUtils.getQualifiedNameTillGivenField(descriptionField.getNode(), YAMLFieldNameConstants.STAGE);
      yamlPropertiesMap.put(descriptionField.getNode().getCurrJsonNode().textValue(),
          YamlProperties.newBuilder().setLocalName(getStageLocalName(descriptionLocal)).setFqn(descriptionFQN).build());
    }

    YamlField variablesField = yamlNode.getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      addVariablesForVariables(variablesField, yamlPropertiesMap);
    }
  }

  public void addVariablesForVariables(YamlField variablesField, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode());
        String localName = YamlUtils.getQualifiedNameTillGivenField(uuidNode.getNode(), YAMLFieldNameConstants.STAGE);
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        if (valueNode == null) {
          throw new InvalidRequestException(
              "Variable with name \"" + variableName + "\" added without any value. Fqn: " + fqn);
        }
        yamlPropertiesMap.put(valueNode.getNode().getCurrJsonNode().textValue(),
            YamlProperties.newBuilder().setLocalName(getStageLocalName(localName)).setFqn(fqn).build());
      }
    });
  }

  private String getStageLocalName(String fqn) {
    String[] split = fqn.split("\\.");
    return fqn.replaceFirst(split[0], YAMLFieldNameConstants.STAGE);
  }
}