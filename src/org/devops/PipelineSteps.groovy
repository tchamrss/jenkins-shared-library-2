
package org.devops

class PipelineSteps implements Serializable {

    def script

    PipelineSteps(script) {
        this.script = script
    }

    def checkoutCode(Map config) {
        script.git(
            branch: config.gitBranch ?: 'master',
            credentialsId: config.gitCredentials ?: 'github-creds',
            url: config.gitUrl
        )
    }

    def incrementVersion(String appDir) {
        script.dir(appDir) {
            script.sh 'npm version minor --no-git-tag-version'
        }
    }

    def runTests(String appDir) {
        script.dir(appDir) {
            script.sh 'npm ci'
            script.sh 'npm test -- --runInBand'
        }
    }

    def buildDockerImage(String appDir, String dockerImage) {
        script.dir(appDir) {
            script.env.APP_VERSION = script.sh(
                script: "node -p \"require('./package.json').version\"",
                returnStdout: true
            ).trim()

            script.sh "docker build -t ${dockerImage}:${script.env.APP_VERSION} ."
            script.sh "docker tag ${dockerImage}:${script.env.APP_VERSION} ${dockerImage}:latest"
        }
    }

    def pushDockerImage(String dockerImage, String credentialsId) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'DOCKER_USER',
                passwordVariable: 'DOCKER_TOKEN'
            )
        ]) {
            script.sh '''
                set +x
                echo "$DOCKER_TOKEN" | docker login \
                    -u "$DOCKER_USER" --password-stdin
                docker push "$DOCKER_IMAGE:$APP_VERSION"
                docker push "$DOCKER_IMAGE:latest"
                docker logout
            '''
        }
    }

    def commitAndPushVersion(Map config, String appDir) {
        script.withCredentials([
            script.usernamePassword(
                credentialsId: config.gitCredentials ?: 'github-creds',
                usernameVariable: 'GIT_USER',
                passwordVariable: 'GIT_TOKEN'
            )
        ]) {
            script.sh '''
                set +x
                git config user.name "Jenkins"
                git config user.email "jenkins@example.com"
                git add "$APP_DIR/package.json" "$APP_DIR/package-lock.json"
                git commit -m "Bump app version" || echo "No changes to commit"
            '''

            // Push mit Jenkins-Git-Credentials statt Token in der URL.
            script.sshagent([config.sshCredentials ?: 'github-ssh']) {
                script.sh "git push origin HEAD:${config.gitBranch ?: 'master'}"
            }
        }
    }
}

