
import org.devops.PipelineSteps

def call(Map config = [:]) {
    def pipelineSteps = new PipelineSteps(this)

    pipeline {
        agent any

        environment {
            DOCKER_IMAGE = "${config.dockerImage}"
            APP_DIR = "${config.appDir ?: 'app'}"
            GIT_BRANCH = "${config.gitBranch ?: 'master'}"
        }

        stages {
            stage('Checkout') {
                steps {
                    script {
                        pipelineSteps.checkoutCode(config)
                    }
                }
            }

            stage('Increment Version') {
                steps {
                    script {
                        pipelineSteps.incrementVersion(APP_DIR)
                    }
                }
            }

            stage('Test') {
                steps {
                    script {
                        pipelineSteps.runTests(APP_DIR)
                    }
                }
            }

            stage('Build Docker Image') {
                steps {
                    script {
                        pipelineSteps.buildDockerImage(APP_DIR, DOCKER_IMAGE)
                    }
                }
            }

            stage('Push to Docker Hub') {
                steps {
                    script {
                        pipelineSteps.pushDockerImage(
                            DOCKER_IMAGE,
                            config.dockerCredentials ?: 'dockerhub-creds'
                        )
                    }
                }
            }

            stage('Commit and Push Version') {
                steps {
                    script {
                        pipelineSteps.commitAndPushVersion(config, APP_DIR)
                    }
                }
            }
        }

        post {
            success {
                echo "Pipeline erfolgreich: ${DOCKER_IMAGE}:${APP_VERSION}"
            }
            failure {
                echo 'Pipeline fehlgeschlagen. Bitte Console Output prüfen.'
            }
        }
    }
}
