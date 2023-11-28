def call(dockerRepoName, imageName) {
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh 'pip install -r requirements.txt --break-system-packages'
                sh 'pip install --upgrade flask --break-system-packages'
            }
        }
        stage("Lint") {
            steps {
                sh 'find . -type f -name "*.py" | xargs pylint --fail-under=5.0'
            }
        }
        stage('Security') {
            steps {
                sh 'bandit -r *.py'
            }
        }
        stage('Package') {
            when {
                expression { env.GIT_BRANCH == 'origin/main' }
            }
            steps {
                withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                    sh "docker login -u 'satonohime' -p '$TOKEN' docker.io"
                    sh "docker build -t ${dockerRepoName}:latest --tag satonohime/${dockerRepoName}:${imageName} ."
                    sh "docker push satonohime/${dockerRepoName}:${imageName}"
                }
            }
        }
        stage('Deploy') {
            when {
                expression { env.GIT_BRANCH == 'origin/main' }
            }
            steps {
                script {
                    withCredentials([sshUserPrivateKey(credentialsId: 'rc-kafka-key', keyFileVariable: 'SSH_FILE', usernameVariable: 'SSH_USER')]) {
                        sshAgent(['rc-kafka-key']) {
                            sshCommand remote: [
                                name: 104.42.179.109,
                                host: 104.42.179.109,
                                user: 'SSH_USER',
                                identityFile: 'SSH_FILE',
                                allowAnyHosts: true
                            ], command: """
                                cd /home/azureuser/4850/deployment &&
                                        docker compose down &&
                                        docker image pull satonohime/${dockerRepoName}:${imageName} &&
                                        docker compose up -d
                            """
                        }
                    }
                }
            }
        }
    }
}
}
