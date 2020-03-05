#!/usr/bin/env groovy

pipeline {
	agent { label 'rhel7-micro' }
	
	options {
	   timeout(time: 1, unit: 'HOURS')
	}

	tools {
		maven 'maven3-latest'
		jdk 'openjdk-1.8'
	}
	
	stages {
		stage('Checkout SCM') {
			steps {
				deleteDir()
				git url: "https://github.com/${params.FORK}/rsp-server", branch: params.BRANCH
				stash includes: '**', name: 'source'
			}
		}
		stage ('Running parallel branches') {
			parallel {
				stage ('Java 8 runtime') {    
					agent { label 'rhel7' }
					stages {
						stage('Build Java 8 & unit tests') {
							steps {
								unstash 'source'
								sh 'mvn clean install -fae -B'
								archiveArtifacts 'distribution/distribution*/target/org.jboss.tools.rsp.distribution*.zip,api/docs/org.jboss.tools.rsp.schema/target/*.jar,site/target/repository/**'
								stash includes: 'distribution/distribution*/target/org.jboss.tools.rsp.distribution*.zip,api/docs/org.jboss.tools.rsp.schema/target/*.jar', name: 'zips'
								stash includes: 'site/target/repository/**', name: 'site'
							}
						}
						stage('Integration tests') {
							steps {
								sh 'mvn verify -B -Pintegration-tests -DskipTests=true -Dmaven.test.failure.ignore=true'
								archiveArtifacts 'distribution/integration-tests/target/quickstarts/*/build.log'
							}
						}
						stage('SonarCloud Report') {
							when {
								expression { params.SONAR }
							}
							steps {
								sh 'mvn -B -P sonar sonar:sonar -Dsonar.login=${SONAR_TOKEN}'
							}
						}
					}
					post {
						always {
							junit '**/surefire-reports/*.xml'
							archiveArtifacts '**/integration-tests/target/surefire-reports/*,**/tests/**/target/surefire-reports/*'
						}
					}
				}
				stage ('Java 11 runtime') {
					agent { label 'rhel7' }
					tools {
						jdk 'openjdk-11'
					}
					stages {
				 		stage('Build Java 11 & unit tests') {
				 			steps {
				 				unstash 'source'
				 				sh 'mvn clean install -fae -B'
					    	}
			 			}
					    stage('Integration tests') {
				 			steps {
				 				sh 'mvn verify -B -Pintegration-tests -DskipTests=true -Dmaven.test.failure.ignore=true'
							}
						}
					}
					post {
						always {
							junit '**/surefire-reports/*.xml'
							archiveArtifacts '**/integration-tests/target/surefire-reports/*,**/tests/**/target/surefire-reports/*'
						}
					}
				}
			}
		}
	}
	post {
		success {
			script {
				unstash 'site'
				unstash 'zips'
				def distroVersion = sh script: "ls distribution/distribution/target/*.zip | cut --complement -f 1 -d '-' | rev | cut -c5- | rev | tr -d '\n'", returnStdout: true

				// First empty the remote dirs
				def emptyDir = sh script: "mktemp -d | tr -d '\n'", returnStdout: true
				sh "chmod 775 ${emptyDir}"
				sh "rsync -Pzrlt --rsh=ssh --protocol=28 --delete ${emptyDir}/ ${UPLOAD_USER_AT_HOST}:${UPLOAD_PATH}/snapshots/rsp-server/p2/${distroVersion}/"
				sh "rsync -Pzrlt --rsh=ssh --protocol=28 --delete ${emptyDir}/ ${UPLOAD_USER_AT_HOST}:${UPLOAD_PATH}/snapshots/rsp-server/p2/${distroVersion}/plugins/"
    
    			// Upload the p2 update site.  This logic only works because all plugins are jars. 
    			// If we ever have exploded bundles here, this will need to be redone
				def siteRepositoryFilesToPush = findFiles(glob: 'site/target/repository/*')
				def sitePluginFilesToPush = findFiles(glob: 'site/target/repository/plugins/*')
				for (i = 0; i < siteRepositoryFilesToPush.length; i++) {
					sh "rsync -Pzrlt --rsh=ssh --protocol=28 ${siteRepositoryFilesToPush[i].path} ${UPLOAD_USER_AT_HOST}:${UPLOAD_PATH}/snapshots/rsp-server/p2/${distroVersion}/"
				}
				for (i = 0; i < sitePluginFilesToPush.length; i++) {
					sh "rsync -Pzrlt --rsh=ssh --protocol=28 ${sitePluginFilesToPush[i].path} ${UPLOAD_USER_AT_HOST}:${UPLOAD_PATH}/snapshots/rsp-server/p2/${distroVersion}/plugins/"
				}

				// Upload distributions / zips
				def filesToPush = findFiles(glob: '**/*.zip')
				for (i = 0; i < filesToPush.length; i++) {
					sh "rsync -Pzrlt --rsh=ssh --protocol=28 ${filesToPush[i].path} ${UPLOAD_USER_AT_HOST}:${UPLOAD_PATH}/snapshots/rsp-server/"
				}

			}
		}
	}
}
