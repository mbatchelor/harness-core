#!/usr/bin/env bash

set -ex

local_repo=${HOME}/.m2/repository
if [ "${PLATFORM}" == "jenkins" ]; then
  local_repo=/root/.m2/repository
  if [ ! -z "${DISTRIBUTE_TESTING_WORKER}" ]; then
    bash scripts/bazel/testDistribute.sh
  fi
fi

BAZEL_BIN=$(bazel info bazel-bin 2>/dev/null)

if [[ ! -z "${OVERRIDE_LOCAL_M2}" ]]; then
  local_repo=${OVERRIDE_LOCAL_M2}
fi

if [ "${RUN_BAZEL_FUNCTIONAL_TESTS}" == "true" ]; then

  bazel build -- //190-deployment-functional-tests/...
  curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar --output alpn-boot-8.1.13.v20181017.jar

  bazel run 230-model-test:app &
  MANAGER_PID=$!

  bazel test --keep_going --jobs=3 -- //190-deployment-functional-tests:io.harness.functional.DummyFirstCdFunctionalTest || true

  java -Xbootclasspath/p:alpn-boot-8.1.13.v20181017.jar -Xmx4096m -XX:+HeapDumpOnOutOfMemoryError \
    -XX:+PrintGCDetails -XX:+PrintGCDateStamps -Xloggc:mygclogfilename.gc -XX:+UseParallelGC \
    -XX:MaxGCPauseMillis=500 -jar ${BAZEL_BIN}/260-delegate/module_deploy.jar /harness/260-delegate/config-delegate.yml &
  DELEGATE_PID=$!

  bazel test --keep_going --jobs=3 -- //190-deployment-functional-tests/... || true

  echo "INFO: MANAGER_PID = $MANAGER_PID"
  echo "INFO: DELEGATE_PID = $DELEGATE_PID"

  kill -9 $MANAGER_PID || true
  kill -9 $DELEGATE_PID || true
fi