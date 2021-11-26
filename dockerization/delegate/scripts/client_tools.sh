chmod +x /opt/harness-delegate/*.sh \
&& mkdir -m 777 -p client-tools/kubectl/v1.13.2 \
&& curl -s -L -o client-tools/kubectl/v1.13.2/kubectl https://app.harness.io/public/shared/tools/kubectl/release/v1.13.2/bin/linux/amd64/kubectl \
&& mkdir -m 777 -p client-tools/go-template/v0.4 \
&& curl -s -L -o client-tools/go-template/v0.4/go-template https://app.harness.io/public/shared/tools/go-template/release/v0.4/bin/linux/amd64/go-template \
&& mkdir -m 777 -p client-tools/harness-pywinrm/v0.4-dev \
&& curl -s -L -o client-tools/harness-pywinrm/v0.4-dev/harness-pywinrm https://app.harness.io/public/shared/tools/harness-pywinrm/release/v0.4-dev/bin/linux/amd64/harness-pywinrm \
&& mkdir -m 777 -p client-tools/helm/v2.13.1 && curl -s -L -o client-tools/helm/v2.13.1/helm https://app.harness.io/public/shared/tools/helm/release/v2.13.1/bin/linux/amd64/helm \
&& mkdir -m 777 -p client-tools/helm/v3.1.2 \
&& curl -s -L -o client-tools/helm/v3.1.2/helm https://app.harness.io/public/shared/tools/helm/release/v3.1.2/bin/linux/amd64/helm \
&& mkdir -m 777 -p client-tools/chartmuseum/v0.13.0 \
&& curl -s -L -o client-tools/chartmuseum/v0.13.0/chartmuseum https://app.harness.io/public/shared/tools/chartmuseum/release/v0.13.0/bin/linux/amd64/chartmuseum \
&& mkdir -m 777 -p client-tools/chartmuseum/v0.8.2 \
&& curl -s -L -o client-tools/chartmuseum/v0.8.2/chartmuseum https://app.harness.io/public/shared/tools/chartmuseum/release/v0.8.2/bin/linux/amd64/chartmuseum \
&& mkdir -m 777 -p client-tools/tf-config-inspect/v1.0/linux/amd64 \
&& curl -s -L -o client-tools/tf-config-inspect/v1.0/linux/amd64/terraform-config-inspect https://app.harness.io/public/shared/tools/terraform-config-inspect/v1.0/linux/amd64/terraform-config-inspect \
&& mkdir -m 777 -p client-tools/tf-config-inspect/v1.1/linux/amd64 \
&& curl -s -L -o client-tools/tf-config-inspect/v1.1/linux/amd64/terraform-config-inspect https://app.harness.io/public/shared/tools/terraform-config-inspect/v1.1/linux/amd64/terraform-config-inspect \
&& mkdir -m 777 -p client-tools/oc/v4.2.16 \
&& curl -s -L -o client-tools/oc/v4.2.16/oc https://app.harness.io/public/shared/tools/oc/release/v4.2.16/bin/linux/amd64/oc \
&& mkdir -m 777 -p client-tools/kustomize/v3.5.4 \
&& curl -s -L -o client-tools/kustomize/v3.5.4/kustomize https://app.harness.io/public/shared/tools/kustomize/release/v3.5.4/bin/linux/amd64/kustomize \
&& mkdir -m 777 -p client-tools/kustomize/v4.0.0 \
&& curl -s -L -o client-tools/kustomize/v4.0.0/kustomize https://app.harness.io/public/shared/tools/kustomize/release/v4.0.0/bin/linux/amd64/kustomize \
&& mkdir -m 777 -p client-tools/scm/72f01538/linux/amd64 \
&& curl -s -L -o client-tools/scm/72f01538/linux/amd64/scm https://app.harness.io/public/shared/tools/scm/release/72f01538/bin/linux/amd64/scm