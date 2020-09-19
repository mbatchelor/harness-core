set -e

local_repo=${HOME}/.m2/repository
BAZEL_ARGUMENTS=
if [ "${PLATFORM}" == "jenkins" ]
then
  GCP="--google_credentials=${GCP_KEY}"
  bazelrc=--bazelrc=bazelrc.remote
  local_repo=/root/.m2/repository
  BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --action_env=DISTRIBUTE_TESTING_WORKER=${DISTRIBUTE_TESTING_WORKER}"
  BAZEL_ARGUMENTS="${BAZEL_ARGUMENTS} --action_env=DISTRIBUTE_TESTING_WORKERS=${DISTRIBUTE_TESTING_WORKERS}"
fi

if [[ ! -z "${OVERRIDE_LOCAL_M2}" ]]; then
  local_repo=${OVERRIDE_LOCAL_M2}
fi

if [ "${STEP}" == "dockerization" ]
then
  GCP=""
fi

build_bazel_module() {
  module=$1
  bazel ${bazelrc} build //${module}:module ${GCP} ${BAZEL_ARGUMENTS}

  if [ "${RUN_BAZEL_TESTS}" == "true" ]
  then
    bazel ${bazelrc} test //${module}/... ${GCP} ${BAZEL_ARGUMENTS} || true
  fi

  mvn -B install:install-file \
   -Dfile=bazel-bin/${module}/libmodule.jar \
   -DgroupId=software.wings \
   -DartifactId=${module} \
   -Dversion=0.0.1-SNAPSHOT \
   -Dpackaging=jar \
   -DgeneratePom=true \
   -DpomFile=${module}/pom.xml \
   -DlocalRepositoryPath=${local_repo}
}

build_java_proto_module() {
  module=$1
  modulePath=$module/src/main/proto

  build_proto_module $module $modulePath
}

build_proto_module() {
  module=$1
  modulePath=$2
  bazel ${bazelrc} build //${modulePath}/... ${GCP} ${BAZEL_ARGUMENTS} --javacopt=' -XepDisableAllChecks'

  bazel_library=`echo ${module} | tr '-' '_'`

  mvn -B install:install-file \
   -Dfile=../../bazel-bin/${modulePath}/lib${bazel_library}_java_proto.jar \
   -DgroupId=software.wings \
   -DartifactId=${module}-proto \
   -Dversion=0.0.1-SNAPSHOT \
   -Dpackaging=jar \
   -DgeneratePom=true \
   -DlocalRepositoryPath=${local_repo} \
   -f scripts/bazel/proto_pom.xml
}


build_bazel_module 990-commons-test
build_bazel_module 12-commons
build_bazel_module 13-grpc-api
build_bazel_module 14-api-services-beans
build_java_proto_module 13-grpc-api
build_java_proto_module 19-delegate-tasks-beans
build_java_proto_module 20-delegate-beans
build_java_proto_module 21-delegate-agent-beans
build_java_proto_module 22-delegate-service-beans
build_java_proto_module 50-delegate-task-grpc-service proto

build_proto_module 16-expression-service 16-expression-service/src/main/proto/io/harness/expression/service
build_proto_module ciscm product/ci/scm/proto
build_proto_module ciengine product/ci/engine/proto

rm -f bazel-bin bazel-out bazel-portal bazel-testlogs
