pipeline {
    agent any
    environment {
        REPO_NAME = 'jenkins-test'
        DOCKER_TAG = 'latest'
        GCP_REGION = 'asia-south1'
        GCP_ARTIFACT_REGISTRY = "asia-south1-docker.pkg.dev/gamerjiautomations/sample-jenkins-test"
        APPROVER_EMAIL = 'aishwarya.r@thecloudside.com'
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
       stage('Send Email Notification') {
            steps {
                script {
                    emailext(
                        subject: "Jenkins Pipeline Execution: ${currentBuild.fullDisplayName}",
                        body: """
                            <p>Hello Team,</p>
                            <p>The Jenkins pipeline for <b>${env.REPO_NAME}</b> has completed.</p>
                            <p>Status: ${currentBuild.currentResult}</p>
                            <p>Check console output <a href="${env.BUILD_URL}">here</a>.</p>
                        """,
                        recipientProviders: [[$class: 'DevelopersRecipientProvider']],
                        to: env.APPROVER_EMAIL
                    )
                }
            }
        }
         stage('Wait for Approval') {
            steps {
                input message: "Do you approve the deployment?", ok: "Approve"
                echo "Approval received. Proceeding to the next stage."
            }
        }

        stage('Deploy') {
            steps {
                echo "Deploying the application..."
            }
        }
    }
}


