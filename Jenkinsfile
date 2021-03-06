pipeline {
  options {
    disableConcurrentBuilds()
    quietPeriod(30)
  }
  agent {
    kubernetes {
      // Change the name of jenkins-maven label to be able to use yaml configuration snippet
      label "maven-dind"
      // Inherit from Jx Maven pod template
      inheritFrom "maven"
      // Add pod configuration to Jenkins builder pod template
      yamlFile "maven-dind.yaml"
    }
  }
  environment {
    ORG = 'activiti'
    REALM = "activiti"
    APP_NAME = 'activiti-cloud-application'
    JX_VERSION = jx_release_version()
    CHARTMUSEUM_CREDS = credentials('jenkins-x-chartmuseum')
    GITHUB_CHARTS_REPO = "https://github.com/Activiti/activiti-cloud-helm-charts.git"
    GITHUB_HELM_REPO_URL = "https://activiti.github.io/activiti-cloud-helm-charts/"
    GITHUB_CHARTS_BRANCH = "gh-pages"
    PREVIEW_NAMESPACE = "example-$BRANCH_NAME-$BUILD_NUMBER".toLowerCase().replaceAll("[\\-\\+\\.\\^:,]", "");
    PREVIEW_VERSION = "7.1.0-$BRANCH_NAME-$BUILD_NUMBER";
    GLOBAL_GATEWAY_DOMAIN = "35.242.205.159.nip.io"
    GATEWAY_HOST = "gateway.$PREVIEW_NAMESPACE.$GLOBAL_GATEWAY_DOMAIN"
    SSO_HOST = "identity.$PREVIEW_NAMESPACE.$GLOBAL_GATEWAY_DOMAIN"
    RELEASE_BRANCH = "develop"
    RELEASE_TAG_REGEX = "*M*"
    ACTIVITI_CLOUD_FULL_CHART_VERSIONS = "$VERSION"
    HELM_ACTIVITI_VERSION = "$VERSION"
  }
  stages {
    stage('Configure Git') {
      steps {
        container('maven') {
          sh "git config --global credential.helper store"
          sh "jx step git credentials"
        }
      }
    }
    stage('Set Versions') {
      parallel {
        stage('Preview Version') {
          when {
            branch 'PR-*'
          }
          environment {
            VERSION = "$PREVIEW_VERSION"
          }
          steps {
            container('maven') {
              echo "VERSION=$VERSION"
              // so we can retrieve the version in later steps
              sh "echo $VERSION > VERSION"
            }
          }
        }
        stage('Release Version') {
          when {
            branch "$RELEASE_BRANCH"
          }
          environment {
            VERSION = jx_release_version()
          }
          steps {
            container('maven') {
              echo "VERSION=$VERSION"
              // so we can retrieve the version in later steps
              sh "echo $VERSION > VERSION"

              // ensure we're not on a detached head
              sh "git checkout $RELEASE_BRANCH"
              sh "git fetch --tags"

            }
          }
        }
        stage('Tag Version') {
          when {
            tag "$RELEASE_TAG_REGEX"
          }
          environment {
            VERSION = "$TAG_NAME"
          }
          steps {
            container('maven') {
              echo "VERSION=$VERSION"
              sh "git checkout $VERSION"
              sh "git fetch --all --tags --prune"
              sh "git checkout tags/$VERSION -b $VERSION"

              sh "echo $VERSION > VERSION"
            }
          }
        }
      }
    }

    stage('Set version and install') {
      environment {
        VERSION = version()
      }
      steps {
        container('maven') {
          // ensure we're not on a detached head
          sh "mvn versions:set -DprocessAllModules=true -DgenerateBackupPoms=false -DnewVersion=$VERSION"
          sh "mvn clean install -DskipTests"
        }
      }
    }

    stage('Build Docker images') {

      environment {
        VERSION = version()
      }
      steps {
        container('maven') {

          script {
            def charts = ["activiti-cloud-query/charts/activiti-cloud-query",
                          "example-runtime-bundle/charts/runtime-bundle",
                          "activiti-cloud-modeling/charts/activiti-cloud-modeling",
                          "example-cloud-connector/charts/activiti-cloud-connector"]

            for (chart in charts) {
              dir("$chart") {
                 sh "make version"
              }
            }

            def modules = ["activiti-cloud-query",
                           "example-runtime-bundle",
                           "activiti-cloud-modeling",
                           "example-cloud-connector"]

            for (module in modules) {
              dir("$module") {
                sh "mvn clean install"
                sh "export VERSION=$VERSION && export APP_NAME=$module && skaffold build -f skaffold.yaml"
                sh "jx step post build --image $DOCKER_REGISTRY/$ORG/$module:$VERSION"
              }
            }
          }
        }
      }
    }

    stage('Publish charts') {

      environment {
        VERSION = version()
      }
      steps {
        container('maven') {
          script {

            def GITHUB_CHARTS_DIR = "./target/charts_repo"

            sh """git clone -b "$GITHUB_CHARTS_BRANCH" "$GITHUB_CHARTS_REPO" $GITHUB_CHARTS_DIR """

            def charts = ["activiti-cloud-query/charts/activiti-cloud-query",
                          "example-runtime-bundle/charts/runtime-bundle",
                          "activiti-cloud-modeling/charts/activiti-cloud-modeling",
                          "example-cloud-connector/charts/activiti-cloud-connector"]


            for (chart in charts) {
              dir("$chart") {
                def name = chart.substring(chart.lastIndexOf('/') + 1)
                sh "echo $name"
                sh "rm -rf requirements.lock"
                sh "rm -rf charts"
                sh "rm -rf $name*.tgz"

                sh "helm init --client-only"
                sh "helm repo add charts_activiti https://activiti.github.io/activiti-cloud-charts/"
                sh "helm repo add activiti-cloud-helm-charts https://activiti.github.io/activiti-cloud-helm-charts/"
                sh "helm dependency build"
                sh "helm lint"
                sh "helm install . --name $name --debug --dry-run"
                sh "helm package ."

                def archive = name + "-" + VERSION + ".tgz"
                sh """cp $archive ../../../$GITHUB_CHARTS_DIR """

              }
            }

            sh """cd $GITHUB_CHARTS_DIR && \
                   helm repo index . && \
                   git add . && \
                   git commit -m "fix:(version) release $VERSION " && \
                   git pull --rebase && \
                   git push origin "$GITHUB_CHARTS_BRANCH" """
            sh "rm -rf $GITHUB_CHARTS_DIR"

          }
        }
      }
    }

    stage('Build Release for acceptance test') {
      environment {
        VERSION = version()
      }
      steps {
        container('maven') {
          dir("activiti-cloud-acceptance-scenarios") {
            sh "mvn clean install -DskipTests"
          }

          dir("activiti-cloud-dependencies") {
            sh "mvn clean install -DskipTests"
          }
        }
      }
    }

    stage('Build And Deploy Helm Chart') {

      environment {
        VERSION = version()
      }
      steps {
        container('maven') {

          sh '''updatebot --dry push-version --kind helm activiti-cloud-dependencies $VERSION \
                        runtime-bundle $VERSION \
                        activiti-cloud-connector $VERSION \
                        activiti-cloud-query $VERSION \
                        activiti-cloud-modeling $VERSION
                  '''

          sh """cd  .updatebot-repos/github/activiti/activiti-cloud-full-chart/charts/activiti-cloud-full-example/ && \
                    rm -rf requirements.lock && \
                    rm -rf charts && \
                    rm -rf *.tgz && \
                    helm init --client-only && \
                    helm repo add activiti-cloud-helm-charts https://activiti.github.io/activiti-cloud-helm-charts/ && \
                    helm repo add alfresco https://kubernetes-charts.alfresco.com/stable\t&& \
                    helm repo add alfresco-incubator https://kubernetes-charts.alfresco.com/incubator && \
                    helm dependency build && \
                    helm lint && \
                    helm package . """
          sh """cd  .updatebot-repos/github/activiti/activiti-cloud-full-chart/charts/activiti-cloud-full-example/ && \
            helm upgrade $PREVIEW_NAMESPACE . \
            --install \
            --set global.gateway.domain=$GLOBAL_GATEWAY_DOMAIN \
            --namespace $PREVIEW_NAMESPACE \
            --debug \
            --wait """
          sh "sleep 30"
        }
      }
    }

    stage("Run Acceptance Scenarios") {

      environment {
        VERSION = version()
      }
      parallel {
        stage("Modeling Acceptance Tests") {
          steps {
            container('maven') {
              dir("activiti-cloud-acceptance-scenarios") {
                sh "mvn -pl 'modeling-acceptance-tests' clean verify"
              }
            }
          }
          post {
            failure {
              slackSend(
                channel: "#activiti-community-builds",
                color: "danger",
                message: "FAILED: Activiti Application :: Modeling Acceptance Tests: $BUILD_URL"
              )
            }
          }
        }
        stage("Runtime Acceptance Scenarios") {
          steps {
            container('maven') {
              dir("activiti-cloud-acceptance-scenarios") {
                sh "mvn -pl 'runtime-acceptance-tests' clean verify"
              }
            }
          }
          post {
            failure {
              slackSend(
                channel: "#activiti-community-builds",
                color: "danger",
                message: "FAILED: Activiti Application :: Runtime Acceptance Tests: $BUILD_URL"
              )
            }
          }
        }
      }
      post {
        success {
          slackSend(
            channel: "#activiti-community-builds",
            color: "good",
            message: "SUCCESSFUL: Activiti Application Acceptance Tests: $BUILD_URL"
          )
        }
      }
    }

    stage('Publish Releases') {
      when {
        anyOf {
          tag "$RELEASE_TAG_REGEX";
          branch "$RELEASE_BRANCH";
        }
      }
      environment {
        VERSION = version()
      }
      steps {
        container('maven') {
          script {

            sh "rm -rf .updatebot-repos/github"
            sh "git add --all"
            sh """git commit -m "release $VERSION" --allow-empty """
            sh """git tag -fa v$VERSION -m "Release version $VERSION" """
            sh "git push origin v$VERSION "

            sh "mvn clean deploy -DskipTests"

            def activiti_cloud_version = activiti_cloud_version()
            echo "activiti cloud version:: $activiti_cloud_version"

            retry(5) {
              sh """updatebot push-version --kind maven \
                  org.activiti.cloud:activiti-cloud-dependencies $VERSION \
                  org.activiti.cloud:activiti-cloud-modeling-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-audit-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-api-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-parent $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-connectors-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-messages-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-modeling-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-notifications-graphql-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-query-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-runtime-bundle-dependencies $activiti_cloud_version \
                  org.activiti.cloud:activiti-cloud-service-common-dependencies $activiti_cloud_version \
                  --merge false
                  """

              sh '''updatebot push-version --kind helm activiti-cloud-dependencies $VERSION \
                        runtime-bundle $VERSION \
                        activiti-cloud-connector $VERSION \
                        activiti-cloud-query $VERSION \
                        activiti-cloud-modeling $VERSION
                  '''

              sh '''updatebot push-version --kind make ACTIVITI_CLOUD_ACCEPTANCE_SCENARIOUS_VERSION $VERSION'''
            }
          }
        }
      }
      post {
        success {
          slackSend(channel: "#activiti-community-builds", message: "Activiti Application :: New Helm Chart verison $VERSION released.", sendAsText: true)
        }
      }
    }
  }

  post {
    failure {
      slackSend(
        channel: "#activiti-community-builds",
        color: "danger",
        message: "$BUILD_URL"
      )
    }
    always {
      delete_deployment()
      cleanWs()
    }
  }
}

def delete_deployment() {
  container('maven') {
    sh "helm delete --purge $PREVIEW_NAMESPACE | echo 'try to remove helm chart '$PREVIEW_NAMESPACE"
    sh "kubectl delete namespace $PREVIEW_NAMESPACE | echo 'try to remove namespace '$PREVIEW_NAMESPACE "
  }
}

def jx_release_version() {
  container('maven') {
    return sh(script: "echo \$(jx-release-version)", returnStdout: true).trim()
  }
}

def maven_project_version() {
  container('maven') {
    return sh(script: "echo \$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout -f pom.xml)", returnStdout: true).trim()
  }
}

def hel_version() {
  container('maven') {
    return sh(script: "echo cat VERSION |rev|sed 's/\\./-/'|rev", returnStdout: true).trim()
  }
}


def activiti_cloud_version() {
  container('maven') {
    return sh(script: 'grep -oPm1 "(?<=<activiti-cloud.version>)[^<]+" "activiti-cloud-dependencies/pom.xml"', returnStdout: true).trim()
  }
}

def version() {
  container('maven') {
    return sh(script: "echo \$(cat VERSION)", returnStdout: true).trim()
  }
}
