pipeline {
    agent {
        label 'docker' // Ensure the Jenkins node is labeled as 'docker'
    }
    environment {
        REPO_NAME = 'jenkins-test'
        DOCKER_TAG = 'latest'
        GCP_REGION = 'asia-south1'
        GCP_ARTIFACT_REGISTRY = "asia-south1-docker.pkg.dev/gamerjiautomations/sample-jenkins-test"
    }
    stages {
        stage('Build Docker Image') {
            steps {
                script {
                    echo "Building Docker Image..."
                    sh """
                        docker build -t ${env.REPO_NAME}:${DOCKER_TAG} .
                        docker tag ${env.REPO_NAME}:${DOCKER_TAG} ${GCP_ARTIFACT_REGISTRY}/${env.REPO_NAME}:${DOCKER_TAG}  
                    """
                }
            }
        }

        stage('Push to Artifact Registry') {
            steps {
                script {
                    echo "Authenticating with GCP and pushing Docker image..."
                    sh """
                        gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev
                        docker push ${GCP_ARTIFACT_REGISTRY}/${env.REPO_NAME}:${DOCKER_TAG}
                    """
                }
            }
        }
    }
}
