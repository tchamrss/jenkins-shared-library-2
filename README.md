# Jenkins & Docker DevOps Setup Guide

A structured guide covering how to set up **Jenkins** inside **Docker**, map runtime tools, configure private registries (**Docker Hub** & **Nexus**), manage **Declarative Pipelines**, and prevent automated build loops.

---

## 1. Initial Server Setup & Jenkins Deployment

### Install Docker on the Server
Run the following commands on your newly provisioned Linux server:
```bash
sudo apt update
sudo apt install -y docker.io
sudo systemctl enable --now docker
```

Verify that Docker is up and running:
```bash
docker ps
```

### Spin Up Jenkins
Start Jenkins with a host-mapped persistent configuration directory:
```bash
docker run -p 8080:8080 -p 50000:50000 -d \
  -v jenkins_home:/var/jenkins_home \
  jenkins/jenkins:lts
```
* Access the web UI via your browser: `http://###.###.###.###:8080/`

### Unlock Jenkins
1. SSH into your server and enter the container using its **Container ID**:
   ```bash
   docker exec -it <CONTAINER_ID> bash
   ```
2. Retrieve the initial one-time administrator password:
   ```bash
   cat /var/jenkins_home/secrets/initialAdminPassword
   ```
3. Copy the output hash string and paste it into the unlock prompt on your browser UI.

---

## 2. Environment Configurations Inside the Container

### Install Node.js & npm (Inside Jenkins Container)
To install applications directly matching your container's architecture layout:

1. Access the container shell explicitly as the **root** user:
   ```bash
   docker exec -u 0 -it <CONTAINER_ID> bash
   ```
2. Identify the Linux base distribution layout:
   ```bash
   cat /etc/issue
   ```
3. Update sources and fetch curl dependencies:
   ```bash
   apt update && apt install -y curl
   ```
4. Download and process the Node Source distribution script, then complete installation:
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_lts.x -o nodesource_setup.sh
   bash nodesource_setup.sh
   apt install -y nodejs
   ```
5. Verify successful installation states:
   ```bash
   node -v
   npm -v
   ```

### Working with Freestyle Jobs
You can build a **Freestyle project** in Jenkins to run basic commands or bind code via Git. Use these shell actions on the host server to audit paths:
* **List Jobs:** `docker exec -it <CONTAINER_ID> ls /var/jenkins_home/jobs`
* **Inspect Workspace Build Files:** `docker exec -it <CONTAINER_ID> ls /var/jenkins_home/workspace/`

---

## 3. Enabling Docker Commands Inside Jenkins

To build Docker images inside your Jenkins workflows, you must link the host's Docker engine into the Jenkins container.

### Step 1: Re-launch Jenkins with the Unix Socket Mounted
Stop the existing instance and start a new container mounting `/var/run/docker.sock`:
```bash
docker run -p 8080:8080 -p 50000:50000 -d \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  jenkins/jenkins:lts
```
* **Note:** The `docker.sock` file acts as the primary communication socket connecting the CLI client with the background host daemon.

### Step 2: Install Docker Engine Client inside Jenkins
Enter the container as root and deploy the official installation package:
```bash
docker exec -u 0 -it <CONTAINER_ID> bash

# Install the client CLI tool

 curl https://get.docker.com/ > dockerinstall && chmod +x dockerinstall && ./dockerinstall
```

### Step 3: Configure Socket Permissions
Grant the default `jenkins` non-root internal container user read/write access privileges over the mapped runtime node:
```bash
chmod 666 /var/run/docker.sock
ls -l /var/run/docker.sock
```
this will give every container user full Docker access
Exit out of the root session. You can now re-enter as the regular user to test local workflows:
```bash
docker exec -it <CONTAINER_ID> bash
```

---

## 4. Docker Registry Distribution Configurations

### Option A: Pushing to Public Docker Hub
Add this straight block into your standard Jenkins execution shell parameter:
```bash
docker build -t tchamrss/demo-app:jma-1.0 .
echo "$PASSWORD" | docker login -u "$USERNAME" --password-stdin
docker push tchamrss/demo-app:jma-1.0
```

### Option B: Pushing to a Private/Insecure Nexus Registry
Because insecure external endpoints using raw HTTP connections are blocked by default, edit the configurations file directly **on the hosting server framework**:

1. Edit or generate your daemon properties map file:
   ```bash
   sudo vim /etc/docker/daemon.json
   ```
2. Insert the destination reference:
   ```json
   {
     "insecure-registries": [
       "###.###.###.###:8082"
     ]
   }
   ```
3. Save, close out the editor, and restart Docker:
   ```bash
   sudo systemctl restart docker
   ```
4. Restart your target container instance:
   ```bash
   docker start <CONTAINER_ID>
   ```
5. **Important:** Remember to reset socket file access configurations inside the new container:
   ```bash
   docker exec -u 0 -it <CONTAINER_ID> bash
   chmod 666 /var/run/docker.sock
   ```
6. Implement authentication variables and complete your deployment via Jenkins shell:
   ```bash
   docker build -t ###.###.###.###:8082/java-maven-app:1.1 .
   echo "$PASSWORD" | docker login -u "$USERNAME" --password-stdin ###.###.###.###:8082
   docker push ###.###.###.###:8082/java-maven-app:1.1
   ```

---

## 5. Automated Build Triggers & Multibranch Pipelines

### Git Webhook Automated Trigger
To automatically trigger a build when code is pushed to your Git repository:
1. Navigate to your Git repository **Settings → Webhooks**.
2. Set the Payload URL to: `http://###.###.###.###:8080/github-webhook/`
3. Choose Content type: `application/json`.
4. Select **Just the push event** and save the webhook.
5. In Jenkins, check the box **GitHub hook trigger for GITScm polling** under your job configuration.

### Multibranch Pipelines
A Multibranch Pipeline automatically discovers branches containing a `Jenkinsfile` and handles individual target states programmatically.

```groovy
pipeline {
    agent any
    stages {
        stage('Test Everything') {
            steps {
                echo "Running pipeline steps on branch: ${env.BRANCH_NAME}"
            }
        }
        stage('Production Deployment') {
            when {
                expression { env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'main' }
            }
            steps {
                echo 'Deploying application exclusively to production servers...'
            }
        }
    }
}
```

---

## 6. Preventing Endless Commit Loops

When Jenkins automatically modifies a configuration file (like bumping a version in `pom.xml` or `package.json`) and commits the changes back to your repository, it triggers its own webhook. This creates a perpetual, infinite build loop.

### Strategy 1: Using Commit Message Filters (`[skip ci]`)
Configure your automated Jenkins shell scripts to append standard CI skip flags to the commit message. Most modern Git integrations automatically ignore these:
```bash
git commit -m "chore: bump application version [skip ci]"
git push origin HEAD:master
```

### Strategy 2: Ignore Committer Strategy Plugin
If you are using a **Multibranch Pipeline**, you can explicitly ignore commits made by your Jenkins build bot user profile:
1. Ensure the **Ignore Committer Strategy** plugin is installed in Jenkins.
2. In your Multibranch Project, navigate to **Branch Sources** and look for **Build Strategies**.
3. Add the **Ignore Committer Strategy**.
4. Configure your automated Git script to use a distinct author email:
   ```bash
   git config user.name "Jenkins Bot"
   git config user.email "jenkins-bot@###.###.###.###"
   ```
5. Enter `jenkins-bot@###.###.###.###` into the plugin settings field to prevent loops.

---

## 7. Jenkins Declarative Pipeline Syntax

### Basic Pipeline Boilerplate
```groovy
pipeline {
    agent any
    stages {
        stage('build') {
            steps {
                echo 'Building the application'
            }
        }
        stage('test') {
            steps {
                echo 'testing the application'
            }
        }
        stage('deploy') {
            steps {
                echo 'deploying the application'
            }
        }
    }
}
```

### Global vs. Isolated Stage Credentials
* Default built-in global parameters are queryable here: `http://###.###.###.###:8080/env-vars.html/`
* Use `withCredentials` to bind parameters safely inside specific execution phases. Ensure both the **Credentials Binding Plugin** and the **Credentials Plugin** are installed.


### Stage-scoped Credentials

Another way to deal with credentials is to collect them in a stage when they are only needed there:

```groovy
stage('deploy') {
            steps{
                echo 'deploying the application'
		withCredentials([
			usernamePassword(credentials: 'git-credentials', usernameVariable: USER, 				passwordVariable: PWD)

			]){
			    sh "some script ${USER} ${PWD}"

		}
            }
        }

```

To use this method, we need two plugins: **Credentials Binding Plugin** and **Credentials Plugin**.

### Tools

```groovy

Tools {
     maven "maven-3.9"
    
}
```

```groovy
stages {
        stage('build') { // for display purposes
            steps{
                echo 'Building the application'
		sh "mvn install"
            }
        }
}

```

### Parameters

```groovy

pipeline {
    parameters {
    	
    	choice(name: 'VERSION', choices: ['1.1.0','1.2.0','1.3.0'], description: '')
    	booleanParam(name: 'executeTests', defaultValue: true, description: '')
    }
    agent any
    stages {
        stage('build') { // for display purposes
            steps{
                echo 'Building the application'
            }
        }
        stage('test') {
    	    when {
        		expression {
        		    params.executeTests == true
        		}
    	    }
            steps{
                echo 'testing the application'
                
            }
        }

        stage('deploy') {
        	    
                    steps{
                        echo 'deploying the application'
        		echo "deploying version ${params.VERSION}"
                        
                    }
        }
    }
}


```

### Loading a Script

Create a script called `script.groovy` containing:

```groovy
def buildApp(){
	echo 'building the application'
}
return this

def testdApp(){
	echo 'testing the application'
}
return this

def deployApp(){
	echo 'deploying the application'
        echo "deploying version ${params.VERSION}"
}
return this

```

```groovy
def gv
pipeline {
    parameters {
    	
    	choice(name: 'VERSION', choices: ['1.1.0','1.2.0','1.3.0'], description: '')
    	booleanParam(name: 'executeTests', defaultValue: true, description: '')
    }
    agent any
    stages {

	stage('init') { // for display purposes
            steps{
		script{
		    gv = load "script.groovy"
		}
                
            }
        }

        stage('build') { // for display purposes
            steps{
                script{
		    gv.buildApp()
		}
            }
        }
        stage('test') {
    	    when {
        		expression {
        		    params.executeTests == true
        		}
    	    }
            steps{
                script{
		    gv.testApp()
		}
                
            }
        }

        stage('deploy') {
        	    
                    steps{
                        script{
		    		gv.deploydApp()
			}	
                        
                    }
        }
    }
}




```

### Input Parameters


```groovy
pipeline {
    parameters {
    	
    	choice(name: 'VERSION', choices: ['1.1.0','1.2.0','1.3.0'], description: '')
    	booleanParam(name: 'executeTests', defaultValue: true, description: '')
    }
    agent any
    stages {

	stage('init') { // for display purposes
            steps{
		script{
		    gv = load "script.groovy"
		}
                
            }
        }

        stage('build') { // for display purposes
            steps{
                script{
		    gv.buildApp()
		}
            }
        }
        stage('test') {
    	    when {
        		expression {
        		    params.executeTests == true
        		}
    	    }
            steps{
                script{
		    gv.testApp()
		}
                
            }
        }

        stage('deploy') {
		    input{
			message "Select the environment to deploy to"
			ok "Done"
			parameters {
    	
    			     choice(name: 'ENV', choices: ['dev','staging','prod'], description: '')
   
   		 	}
		    }
		    	
        	    
                    steps{
                        script{
		    		gv.deploydApp()
				echo "deploying to ${ENV}"
			}	
                        
                    }
        }
    }
}




```

Another way to request an input parameter:

```groovy
script{
		    		env.ENV input message "Select the environment to deploy to", ok "Done", parameters: [choice(name: 'ONE', choices: ['dev','staging','prod'], description: '')]
				gv.deploydApp()
				echo "deploying to ${ENV}"
			}	
```



### Complete Pipeline


```groovy
def gv

pipeline {
    agent any
    tools {
        maven 'maven-3.9'
    }
    stages {
        stage(' init') {
            steps {
                script {
                    gv = load "script.groovy"
                }
            }
        }
        stage('Checkout') {
            steps {
                git 'https://gitlab.com/twn-devops-bootcamp/latest/08-jenkins/java-maven-app.git'
            }
        }

        stage('build jar') { 
            steps{
                script {
                    echo 'Building the application'
                    sh 'mvn package'
                }
            }
        }
        stage('build image') { 
            steps{
                script {
                    echo 'Building the docker image'
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-repo', passwordVariable: 'DOCKER_HUB_PASSWORD', usernameVariable: 'DOCKER_HUB_USERNAME')]) {
                        sh 'docker build -t tchamrss/demo-app:jma-2.0 .'
                        sh 'echo $DOCKER_HUB_PASSWORD | docker login -u $DOCKER_HUB_USERNAME --password-stdin'
                        sh 'docker push tchamrss/demo-app:jma-2.0'
                    }
                }
            }
        }
        
        stage('deploy') {
            steps{
                script {
                    echo 'deploying the application'
                }
            }
        }
    }
}


```

### Multibranch Pipeline


```groovy
pipeline {
    agent any
    
    stages {
        stage('test') {
            steps{
                echo 'testing the application'
                echo "executing pipeline for branch: $BRANCH_NAME"
                
            }
        }
        stage('build') { // for display purposes
            when {
                expression { 
                    BRANCH_NAME == 'master'
                 }
            }
            steps{
                echo 'Building the application'
            }
        }
        
        stage('deploy') {
            when {
                expression { 
                    BRANCH_NAME == 'master'
                 }
            }
            steps{
                echo 'deploying the application'
            }
        }
    }
}


```

### Jenkins Credentials

- **System credentials** are available only to the Jenkins server.
- **Global credentials** are available to all pipelines.
- **Project/pipeline credentials** are available only to the project or pipeline.


### Jenkins Shared Library

Benefits include code reusability, easy maintenance, faster pipeline creation, consistency and standardization, and improved collaboration.

1. **Create the shared library**
   - Create a Groovy project in the IDE.
   - Add a `vars` folder and a Groovy script inside it.
   - Create a GitHub repository and push the Groovy project to it.

2. **Make the shared library available in Jenkins**
   - In Manage Jenkins, under System, add the shared library as a global library.

3. **Use the shared library in the Jenkinsfile.**
```groovy
@Library('jenkins-shared-library') _

pipeline {
    agent any
    stages {
        stage('build') {
            steps {
                buildApp()
            }
        }
    }
}
```