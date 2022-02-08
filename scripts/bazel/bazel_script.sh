#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

#local_repo=${HOME}/.m2/repository
BAZEL_ARGUMENTS=
if [ "${PLATFORM}" == "jenkins" ]; then
  bazelrc=--bazelrc=bazelrc.remote
  #local_repo=/root/.m2/repository
  if [ ! -z "${DISTRIBUTE_TESTING_WORKER}" ]; then
    bash scripts/bazel/testDistribute.sh
  fi
fi

BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --show_timestamps --announce_rc"

BAZEL_DIRS=${HOME}/.bazel-dirs
BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --experimental_convenience_symlinks=normal --symlink_prefix=${BAZEL_DIRS}/"

#if [[ ! -z "${OVERRIDE_LOCAL_M2}" ]]; then
#  local_repo=${OVERRIDE_LOCAL_M2}
#fi

# Enable caching by default. Turn it off by exporting CACHE_TEST_RESULTS=no
# to generate full call-graph for Test Intelligence
if [[ ! -z "${CACHE_TEST_RESULTS}" ]]; then
  export CACHE_TEST_RESULTS_ARG=--cache_test_results=${CACHE_TEST_RESULTS}
fi

bazel ${bazelrc} build ${BAZEL_ARGUMENTS}  //:resource
cat ${BAZEL_DIRS}/out/stable-status.txt
cat ${BAZEL_DIRS}/out/volatile-status.txt

if [ "${RUN_BAZEL_TESTS}" == "true" ]; then
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -- //... -//product/... -//commons/... \
  && bazel ${bazelrc} test ${CACHE_TEST_RESULTS_ARG} --define=HARNESS_ARGS=${HARNESS_ARGS} --keep_going ${BAZEL_ARGUMENTS} -- \
  //... -//product/... -//commons/... -//200-functional-test/... -//190-deployment-functional-tests/...
  exit $?
fi

if [ "${RUN_CHECKS}" == "true" ]; then
  TARGETS=$(bazel query 'attr(tags, "checkstyle", //...:*)')
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS}
  exit $?
fi

if [ "${RUN_PMDS}" == "true" ]; then
  TARGETS=$(bazel query 'attr(tags, "pmd", //...:*)')
  bazel ${bazelrc} build ${BAZEL_ARGUMENTS} -k ${TARGETS}
  exit $?
fi

BAZEL_MODULES="\
  //800-pipeline-service:module \
  //820-platform-service:module \
  //820-platform-service:module_deploy.jar \
  //830-notification-service:module \
  //830-resource-group:module \
  //835-notification-senders:module \
  //878-pipeline-service-utilities:module \
  //890-sm-core:module \
  //910-delegate-service-driver:module \
  //920-delegate-service-beans/src/main/proto:all \
  //920-delegate-service-beans:module \
  //925-access-control-service:module \
  //930-delegate-tasks:module \
  //930-ng-core-clients:module \
  //940-ng-audit-service:module \
  //940-notification-client:module \
  //940-notification-client:module_deploy.jar \
  //940-resource-group-beans:module \
  //942-enforcement-sdk:module \
  //945-account-mgmt:module \
  //945-ng-audit-client:module \
  //946-access-control-aggregator:module \
  //947-access-control-core:module \
  //948-access-control-sdk:module \
  //949-access-control-commons:module \
  //950-delegate-tasks-beans/src/main/proto:all \
  //950-delegate-tasks-beans:module \
  //950-ng-project-n-orgs:module \
  //950-wait-engine:module \
  //951-ng-audit-commons:module \
  //953-events-api/src/main/proto:all \
  //953-events-api:module \
  //955-filters-sdk:module \
  //955-outbox-sdk:module \
  //958-migration-sdk:module \
  //960-ng-core-beans:module \
  //960-notification-beans/src/main/proto:all \
  //960-notification-beans:module \
  //960-persistence:module \
  //960-persistence:supporter-test \
  //970-api-services-beans:module \
  //970-grpc:module \
  //970-ng-commons:module \
  //980-commons:module \
  //990-commons-test:module \
  //999-annotations:module \
"

bazel ${bazelrc} build $BAZEL_MODULES ${BAZEL_ARGUMENTS} --remote_download_outputs=all

build_bazel_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_tests() {
  module=$1
  BAZEL_MODULE="//${module}:supporter-test"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_application() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  bazel ${bazelrc} build $BAZEL_MODULES ${BAZEL_ARGUMENTS}

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  if ! grep -q "$BAZEL_DEPLOY_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_DEPLOY_MODULE is not in the list of modules"
    exit 1
  fi
}

build_bazel_application_module() {
  module=$1
  BAZEL_MODULE="//${module}:module"
  BAZEL_DEPLOY_MODULE="//${module}:module_deploy.jar"

  if [ "${BUILD_BAZEL_DEPLOY_JAR}" == "true" ]; then
    bazel ${bazelrc} build $BAZEL_DEPLOY_MODULE ${BAZEL_ARGUMENTS}
  fi

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi
}

build_java_proto_module() {
  module=$1
  modulePath=$module/src/main/proto

  build_proto_module $module $modulePath
}

build_proto_module() {
  module=$1
  modulePath=$2

  BAZEL_MODULE="//${modulePath}:all"

  if ! grep -q "$BAZEL_MODULE" <<<"$BAZEL_MODULES"; then
    echo "$BAZEL_MODULE is not in the list of modules"
    exit 1
  fi

  bazel_library=$(echo ${module} | tr '-' '_')
}

build_protocol_info(){
  module=$1
  moduleName=$2

  bazel query "deps(//${module}:module)" | grep -i "KryoRegistrar" | rev | cut -f 1 -d "/" | rev | cut -f 1 -d "." > /tmp/KryoDeps.text
  cp scripts/interface-hash/module-deps.sh .
  sh module-deps.sh //${module}:module > /tmp/ProtoDeps.text
  bazel ${bazelrc} run ${BAZEL_ARGUMENTS}  //001-microservice-intfc-tool:module -- kryo-file=/tmp/KryoDeps.text proto-file=/tmp/ProtoDeps.text ignore-json | grep "Codebase Hash:" > ${moduleName}-protocol.info
  rm module-deps.sh /tmp/ProtoDeps.text /tmp/KryoDeps.text
}

build_bazel_application 940-notification-client
build_bazel_application 820-platform-service

build_bazel_module 830-notification-service
build_bazel_module 830-resource-group
build_bazel_module 835-notification-senders
build_bazel_module 878-pipeline-service-utilities
build_bazel_module 890-sm-core
build_bazel_module 910-delegate-service-driver
build_bazel_module 920-delegate-service-beans
build_bazel_module 930-delegate-tasks
build_bazel_module 930-ng-core-clients
build_bazel_module 940-ng-audit-service
build_bazel_module 940-resource-group-beans
build_bazel_module 945-ng-audit-client
build_bazel_module 946-access-control-aggregator
build_bazel_module 947-access-control-core
build_bazel_module 948-access-control-sdk
build_bazel_module 949-access-control-commons
build_bazel_module 950-delegate-tasks-beans
build_bazel_module 950-ng-project-n-orgs
build_bazel_module 950-wait-engine
build_bazel_module 951-ng-audit-commons
build_bazel_module 953-events-api
build_bazel_module 955-filters-sdk
build_bazel_module 955-outbox-sdk
build_bazel_module 958-migration-sdk
build_bazel_module 960-ng-core-beans
build_bazel_module 960-notification-beans
build_bazel_module 960-persistence
build_bazel_module 970-api-services-beans
build_bazel_module 970-grpc
build_bazel_module 970-ng-commons
build_bazel_module 980-commons
build_bazel_module 990-commons-test
build_bazel_module 999-annotations


