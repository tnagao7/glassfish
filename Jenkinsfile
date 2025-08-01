/*
* Copyright (c) 2022, 2025 Contributors to the Eclipse Foundation
* Copyright (c) 2018, 2021 Oracle and/or its affiliates. All rights reserved.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0, which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* This Source Code may also be made available under the following Secondary
* Licenses when the conditions for such availability set forth in the
* Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
* version 2 with the GNU Classpath Exception, which is available at
* https://www.gnu.org/software/classpath/license.html.
*
* SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
*/


def dumpSysInfo() {
   sh """
   id || true
   uname -a || true
   env | sort || true
   df -h || true
   \${JAVA_HOME}/bin/jcmd || true
   mvn -version || true
   ant -version || true
   ps -e -o start,etime,pid,rss,drs,command || true
   lscpu || true
   cat /proc/meminfo || true
   ulimit -a || true
   """
}

def startVmstatLogging(String stageName) {
   sh """
   mkdir -p "${WORKSPACE}/logs"
   vmstat -t -w -a -y 10 > "${WORKSPACE}/logs/vmstat-${stageName}.log" 2>&1 & echo \$! > "${WORKSPACE}/vmstat.pid"
   """
}

def stopVmstatLogging() {
   sh """
   if [ -f "${WORKSPACE}/vmstat.pid" ]; then
      pkill -F "${WORKSPACE}/vmstat.pid" || true
      rm -f "${WORKSPACE}/vmstat.pid"
   fi
   df -h || true
   """
   archiveArtifacts artifacts: "logs/*", allowEmptyArchive: true
}

def antjobs = [
    "cdi_all",
    "ql_gf_full_profile_all",
    "ql_gf_web_profile_all",
    "web_jsp",
    "ejb_group_1",
    "ejb_group_2",
    "ejb_group_3",
    "ejb_group_embedded",
    "batch_all",
    "connector_group_1",
    "connector_group_2",
    "connector_group_3",
    "connector_group_4",
    "jdbc_group1",
    "jdbc_group2",
    "jdbc_group3",
    "jdbc_group4",
    "jdbc_group5",
    "persistence_all",
    "naming_all",
    "deployment_all",
    "security_all",
    "webservice_all"
]

def parallelStagesMap = antjobs.collectEntries {
   ["${it}": generateAntPodTemplate(it)]
}

def generateAntPodTemplate(job) {
   return {
      node {
         stage("${job}") {
            try {
               startVmstatLogging("ant-${job}")
               unstash 'build-bundles'
               timeout(time: 2, unit: 'HOURS') {
                  withAnt(installation: 'apache-ant-latest') {
                     dumpSysInfo()
                     sh """
                     mkdir -p ${WORKSPACE}/appserver/tests
                     tar -xzf ${WORKSPACE}/bundles/appserv_tests.tar.gz -C ${WORKSPACE}/appserver/tests
                     export CLASSPATH=${WORKSPACE}/glassfish7/javadb
                     ${WORKSPACE}/appserver/tests/gftest.sh run_test ${job}
                     """
                  }
               }
            } finally {
               stopVmstatLogging()
               archiveArtifacts artifacts: "${job}-results.tar.gz"
               junit testResults: 'results/junitreports/*.xml', allowEmptyResults: false
            }
         }
      }
   }
}

pipeline {

   agent {
      kubernetes {
         inheritFrom "basic"
      yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.9.9-eclipse-temurin-17
    command:
    - cat
    tty: true
    env:
    - name: "HOME"
      value: "/home/jenkins"
    - name: "MAVEN_OPTS"
      value: "-Duser.home=/home/jenkins -Xmx2500m -Xss512k -XX:+UseG1GC -XX:+UseStringDeduplication -Xlog:gc"
    volumeMounts:
    - name: "jenkins-home"
      mountPath: "/home/jenkins"
      readOnly: false
    - name: maven-repo-shared-storage
      mountPath: /home/jenkins/.m2/repository
    - name: settings-xml
      mountPath: /home/jenkins/.m2/settings.xml
      subPath: settings.xml
      readOnly: true
    - name: settings-security-xml
      mountPath: /home/jenkins/.m2/settings-security.xml
      subPath: settings-security.xml
      readOnly: true
    - name: maven-repo-local-storage
      mountPath: "/home/jenkins/.m2/repository/org/glassfish/main"
    resources:
      limits:
        memory: "8Gi"
        cpu: "5500m"
      requests:
        memory: "8Gi"
        cpu: "5500m"
  volumes:
  - name: "jenkins-home"
    emptyDir:
      sizeLimit: "4Gi"
  - name: maven-repo-shared-storage
    persistentVolumeClaim:
      claimName: glassfish-maven-repo-storage
  - name: settings-xml
    secret:
      secretName: m2-secret-dir
      items:
      - key: settings.xml
        path: settings.xml
  - name: settings-security-xml
    secret:
      secretName: m2-secret-dir
      items:
      - key: settings-security.xml
        path: settings-security.xml
  - name: maven-repo-local-storage
    emptyDir:
      sizeLimit: "2Gi"
"""
      }
   }

   environment {
      S1AS_HOME = "${WORKSPACE}/glassfish7/glassfish"
      APS_HOME = "${WORKSPACE}/appserver/tests/appserv-tests"
      TEST_RUN_LOG = "${WORKSPACE}/tests-run.log"
      PORT_ADMIN=4848
      PORT_HTTP=8080
      PORT_HTTPS=8181
   }

   options {
      buildDiscarder(logRotator(numToKeepStr: '2'))

      parallelsAlwaysFailFast()

      // to allow re-running a test stage
      preserveStashes()

      // issue related to default 'implicit' checkout, disable it
      skipDefaultCheckout()

      // abort pipeline if previous stage is unstable
      skipStagesAfterUnstable()

      // show timestamps in logs
      timestamps()

      // global timeout, abort after 6 hours
      timeout(time: 6, unit: 'HOURS')
   }

   stages {

      stage('build') {
         steps {
            checkout scm
            container('maven') {
               script {
               try {
                  startVmstatLogging('mvn-build')
                  dumpSysInfo()
                  timeout(time: 10, unit: 'MINUTES') {
                     sh '''
                     # Validate the structure in all submodules (especially version ids)
                     mvn -B -e -fae clean validate -Ptck,set-version-id,staging

                     # Until we fix ANTLR in cmp-support-sqlstore, broken in parallel builds. Just -Pfast after the fix.
                     mvn -B -e install -Pfastest,staging,ci -T4C
                     ./gfbuild.sh archive_bundles
                     ./gfbuild.sh archive_embedded

                     mvn -B -e clean -Pstaging
                     tar -c -C ${WORKSPACE}/appserver/tests common_test.sh gftest.sh appserv-tests quicklook | gzip --fast > ${WORKSPACE}/bundles/appserv_tests.tar.gz
                     ls -la ${WORKSPACE}/bundles
                     ls -la ${WORKSPACE}/embedded
                     '''
                  }
               } finally {
                  stopVmstatLogging()
               }
               }
            }
            archiveArtifacts artifacts: 'bundles/*.zip', onlyIfSuccessful: true
            archiveArtifacts artifacts: 'embedded/*', onlyIfSuccessful: true
            stash includes: 'bundles/*', name: 'build-bundles'
         }
      }

      stage('Test') {
         parallel {
            stage('mvn-tests') {
               steps {
                  checkout scm
                  container('maven') {
                     script {
                     try {
                        startVmstatLogging('mvn-tests')
                        dumpSysInfo()
                        timeout(time: 2, unit: 'HOURS') {
                           sh '''
                           mvn -B -e clean install -Pstaging,qa,ci
                           '''
                        }
                     } finally {
                        stopVmstatLogging()
                     }
                     }
                  }
               }
               post {
                  always {
                     archiveArtifacts artifacts: "**/server.log*", onlyIfSuccessful: false
                     junit testResults: '**/*-reports/*.xml', allowEmptyResults: false
                  }
               }
            }

            stage('ant-tests') {
               agent {
                  kubernetes {
                     inheritFrom 'basic'
                  }
               }
               tools {
                  jdk 'temurin-jdk17-latest'
                  maven 'apache-maven-3.9.9'
               }
               steps {
                  script {
                     parallel parallelStagesMap
                  }
               }
            }
         }
      }
   }
}
