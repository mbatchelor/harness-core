### Dockerization of Manager ####### Doc
set -x
set -e

mkdir -p dist ;
cd dist

cp -R ../scripts/jenkins/ .
cd ..

curl https://storage.googleapis.com/harness-prod-public/public/shared/tools/alpn/release/8.1.13.v20181017/alpn-boot-8.1.13.v20181017.jar  --output alpn-boot-8.1.13.v20181017.jar

mkdir -p dist/manager ;

cd dist/manager

BAZEL_BIN=$(bazel info bazel-bin 2>/dev/null)

cp ${BAZEL_BIN}/360-cg-manager/module_deploy.jar rest-capsule.jar
cp ../../400-rest/src/main/resources/hazelcast.xml .
cp ../../keystore.jks .
cp ../../360-cg-manager/key.pem .
cp ../../360-cg-manager/cert.pem .
cp ../../360-cg-manager/newrelic.yml .
cp ../../360-cg-manager/config.yml .
cp ../../400-rest/src/main/resources/redisson-jcache.yaml .
cp ../../alpn-boot-8.1.13.v20181017.jar .

cp ../../dockerization/manager/Dockerfile-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/manager/Dockerfile-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/manager/scripts/ .
mv scripts/start_process_bazel.sh scripts/start_process.sh

cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/cv-nextgen ;

cd dist/cv-nextgen

cp ${BAZEL_BIN}/300-cv-nextgen/module_deploy.jar cv-nextgen-capsule.jar
cp ../../300-cv-nextgen/keystore.jks .
cp ../../300-cv-nextgen/cv-nextgen-config.yml .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../300-cv-nextgen/src/main/resources/redisson-jcache.yaml .


cp ../../dockerization/cv-nextgen/Dockerfile-verification-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/cv-nextgen/Dockerfile-verification-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -R ../../dockerization/cv-nextgen/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/verification-service ;

cd dist/verification-service

cp ${BAZEL_BIN}/270-verification/module_deploy.jar verification-capsule.jar
cp ../../270-verification/keystore.jks .
cp ../../270-verification/verification-config.yml .

cp ../../dockerization/verification/Dockerfile-verification-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/verification/Dockerfile-verification-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -R ../../dockerization/verification/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/command-library-server ;

cd dist/command-library-server

cp ${BAZEL_BIN}/210-command-library-server/module_deploy.jar command-library-app-capsule.jar
cp ../../210-command-library-server/keystore.jks .
cp ../../210-command-library-server/command-library-server-config.yml .
cp ../../alpn-boot-8.1.13.v20181017.jar .

cp ../../dockerization/command-library-server/Dockerfile-command-library-server-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/command-library-server/Dockerfile-command-library-server-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -R ../../dockerization/command-library-server/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/event-server ;
cd dist/event-server
cp ${BAZEL_BIN}/350-event-server/module_deploy.jar event-server-capsule.jar
cp ../../350-event-server/key.pem .
cp ../../350-event-server/cert.pem .
cp ../../350-event-server/event-service-config.yml .
cp ../../dockerization/event-server/Dockerfile-event-server-jenkins-k8-openjdk Dockerfile
cp ../../dockerization/event-server/Dockerfile-event-server-jenkins-k8-gcr-openjdk Dockerfile-gcr
cp -r ../../dockerization/event-server/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..

mkdir -p dist/batch-processing ;
cd dist/batch-processing
cp ${BAZEL_BIN}/280-batch-processing/module_deploy.jar batch-processing-capsule.jar
cp ../../280-batch-processing/batch-processing-config.yml .
cp ../../dockerization/batch-processing/Dockerfile-batch-processing-jenkins-k8-openjdk Dockerfile
cp ../../dockerization/batch-processing/Dockerfile-batch-processing-jenkins-k8-gcr-openjdk Dockerfile-gcr
cp -r ../../dockerization/batch-processing/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..

mkdir -p dist/change-data-capture ;
cd dist/change-data-capture
cp ${BAZEL_BIN}/110-change-data-capture/module_deploy.jar change-data-capture.jar
cp ../../110-change-data-capture/config.yml .
cp ../../dockerization/change-data-capture/Dockerfile-change-data-capture-jenkins-k8-openjdk Dockerfile
cp ../../dockerization/change-data-capture/Dockerfile-change-data-capture-jenkins-k8-gcr-openjdk Dockerfile-gcr
cp -r ../../dockerization/change-data-capture/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..

MODULE_NAME="340-ce-nextgen";
FOLDER_NAME="ce-nextgen";
mkdir -p dist/${FOLDER_NAME} ;
cd dist/${FOLDER_NAME}
cp ${BAZEL_BIN}/${MODULE_NAME}/module_deploy.jar ce-nextgen-capsule.jar
cp ../../${MODULE_NAME}/keystore.jks .
cp ../../${MODULE_NAME}/config.yml .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/${FOLDER_NAME}/Dockerfile-ce-nextgen-jenkins-k8-gcr-openjdk Dockerfile-gcr
cp ../../dockerization/${FOLDER_NAME}/Dockerfile-ce-nextgen-jenkins-k8-openjdk Dockerfile
cp -r ../../dockerization/${FOLDER_NAME}/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..

mkdir -p dist/delegate
cp 260-delegate/target/delegate-capsule.jar dist/delegate/delegate-capsule.jar
#cp ${BAZEL_BIN}/260-delegate/module_deploy.jar dist/delegate/delegate-capsule.jar
cp 260-delegate/config-delegate.yml dist/delegate/config-delegate.yml
jarsigner -storetype pkcs12 -keystore ${KEY_STORE} -storepass ${KEY_STORE_PASSWORD} dist/delegate/delegate-capsule.jar ${KEY_STORE_ALIAS}
cp dist/delegate/delegate-capsule.jar delegate-${VERSION}.jar
cp protocol.info dist/delegate/.

mkdir -p dist/watcher
cp ${BAZEL_BIN}/250-watcher/module_deploy.jar dist/watcher/watcher-capsule.jar
jarsigner -storetype pkcs12 -keystore ${KEY_STORE} -storepass ${KEY_STORE_PASSWORD} dist/watcher/watcher-capsule.jar ${KEY_STORE_ALIAS}
cp dist/watcher/watcher-capsule.jar watcher-${VERSION}.jar
cp protocol.info dist/watcher/.

mkdir -p dist/disconnected_on_prem_pov
cd dist/disconnected_on_prem_pov
cp -r ../../on-prem/harness_disconnected_on_prem_pov_final .
cp -r ../../on-prem/disconnected_on_prem_pov_installer .
tar -zcvf disconnected_on_prem_pov_template.tar.gz *
cd ../..
cp dist/disconnected_on_prem_pov/disconnected_on_prem_pov_template.tar.gz disconnected_on_prem_pov_template.tar.gz

mkdir -p dist/disconnected_on_prem_k8s
cd dist/disconnected_on_prem_k8s
cp -r ../../on-prem/kubernetes_installer .
tar -zcvf disconnected_on_prem_k8s_installer_builder.tar.gz *
cd ../..
cp dist/disconnected_on_prem_k8s/disconnected_on_prem_k8s_installer_builder.tar.gz disconnected_on_prem_k8s_installer_builder.tar.gz

mkdir -p dist/test
cd dist/test
cp ${BAZEL_BIN}/160-model-gen-tool/module_deploy.jar model-gen-tool-capsule.jar
cp ../../160-model-gen-tool/config-datagen.yml .
cd ../..

mkdir -p dist/delegate-proxy
cd dist/delegate-proxy
cp ../../dockerization/delegate-proxy/setup.sh .
cp ../../dockerization/delegate-proxy/Dockerfile .
cp ../../dockerization/delegate-proxy/Dockerfile-gcr .
cp ../../dockerization/delegate-proxy/nginx.conf .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi
cd ../..

mkdir -p dist/ng-manager
cd dist/ng-manager

cp ${BAZEL_BIN}/120-ng-manager/module_deploy.jar ng-manager-capsule.jar
cp ../../120-ng-manager/config.yml .
cp ../../keystore.jks .
cp ../../120-ng-manager/key.pem .
cp ../../120-ng-manager/cert.pem .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../120-ng-manager/src/main/resources/redisson-jcache.yaml .

cp ../../dockerization/ng-manager/Dockerfile-ng-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/ng-manager/Dockerfile-ng-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/ng-manager/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..


mkdir -p dist/ci-manager
cd dist/ci-manager
cp ${BAZEL_BIN}/310-ci-manager/module_deploy.jar ci-manager-capsule.jar
cp ../../310-ci-manager/ci-manager-config.yml .
cp ../../keystore.jks .
cp ../../310-ci-manager/key.pem .
cp ../../310-ci-manager/cert.pem .
cp ../../310-ci-manager/src/main/resources/redisson-jcache.yaml .

cp ../../alpn-boot-8.1.13.v20181017.jar .

cp ../../dockerization/ci-manager/Dockerfile-ci-manager-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/ci-manager/Dockerfile-ci-manager-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/ci-manager/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/platform-service
cd dist/platform-service

cp ${BAZEL_BIN}/820-platform-service/module_deploy.jar platform-service-capsule.jar
cp ../../820-platform-service/config.yml .
cp ../../820-platform-service/keystore.jks .
cp ../../820-platform-service/key.pem .
cp ../../820-platform-service/cert.pem .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/platform-service/Dockerfile-platform-service-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/platform-service/Dockerfile-platform-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/platform-service/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/pipeline-service
cd dist/pipeline-service

cp ${BAZEL_BIN}/800-pipeline-service/module_deploy.jar pipeline-service-capsule.jar
cp ../../800-pipeline-service/config.yml .
cp ../../800-pipeline-service/keystore.jks .
cp ../../800-pipeline-service/key.pem .
cp ../../800-pipeline-service/cert.pem .
cp ../../800-pipeline-service/src/main/resources/redisson-jcache.yaml .

cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/pipeline-service/Dockerfile-pipeline-service-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/pipeline-service/Dockerfile-pipeline-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/pipeline-service/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..


mkdir -p dist/eventsapi-monitor
cd dist/eventsapi-monitor

cp ${BAZEL_BIN}/950-events-framework-monitor/module_deploy.jar eventsapi-monitor-capsule.jar
cp ../../950-events-framework-monitor/config.yml .
cp ../../950-events-framework-monitor/redis/* .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/eventsapi-monitor/Dockerfile-eventsapi-monitor-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/eventsapi-monitor/Dockerfile-eventsapi-monitor-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/eventsapi-monitor/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..

mkdir -p dist/accesscontrol-service
cd dist/accesscontrol-service

cp ${BAZEL_BIN}/925-access-control-service/module_deploy.jar accesscontrol-service-capsule.jar
cp ../../925-access-control-service/config.yml .
cp ../../925-access-control-service/keystore.jks .
cp ../../alpn-boot-8.1.13.v20181017.jar .
cp ../../dockerization/accesscontrol-service/Dockerfile-accesscontrol-service-jenkins-k8-openjdk ./Dockerfile
cp ../../dockerization/accesscontrol-service/Dockerfile-accesscontrol-service-jenkins-k8-gcr-openjdk ./Dockerfile-gcr
cp -r ../../dockerization/accesscontrol-service/scripts/ .
cp ../../protocol.info .
echo ${JDK} > jdk.txt
echo ${VERSION} > version.txt
if [ ! -z ${PURPOSE} ]
then
    echo ${PURPOSE} > purpose.txt
fi

cd ../..