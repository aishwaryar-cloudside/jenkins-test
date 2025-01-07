pipeline {
    agent any // Define the agent that will execute the pipeline
    environment {
        REPO_NAME = 'your-docker-repo-name' // Replace with your actual Docker repo name
        DOCKER_TAG = 'latest' // Replace with your desired Docker tag
        GCP_REGION = 'your-gcp-region' // Replace with your GCP region
        GCP_ARTIFACT_REGISTRY = "asia-south1-docker.pkg.dev/gamerjiautomations/sample-jenkins-test" // Update this with your actual Artifact Registry pathasia-south1-docker.pkg.dev/gamerjiautomations/sample-jenkins-test
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
