pipeline {
    agent any
    environment {
        COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
        DOCKER_TAG = "latest"
        REPO_NAME = "docker-build"
        GCP_REGION = "asia-south1"
        GCP_ARTIFACT_REGISTRY = "asia-south1-docker.pkg.dev/gamerjiautomations/sample-jenkins-test"
        GCP_PROJECT_ID = "gamerjiautomations"
        APPROVER_EMAIL = 'megha.sharma@thecloudside.com'
        PM1_EMAIL = 'aishwarya.r@thecloudside.com'
        ADMIN_EMAIL = 'aishwarya.r@thecloudside.com'
        JOB_URL = "${env.JENKINS_URL}job/${env.JOB_NAME}/"
    }
    stages {
        stage('Prepare') {
            steps {
                script {
                    echo "${env.BUILD_NUMBER}"
                    echo "${env.GIT_COMMIT}"
                    echo "${env.BRANCH_NAME}"
                    echo "${env.BUILD_TAG}"
                    echo "${env.JOB_NAME}"
                    echo "Commit ID: ${env.COMMIT_ID}"
                    sh "printenv"
                }
            }
            post {
                always {
                    echo 'Post-build actions completed'
                }
                failure {
                    echo 'Sending failure notification'
                }
            }
        }

        stage('First Approval') {
            when {
                expression { params.deploy.toString() == "prod" }
            }
            steps {
                script {
                    sendApprovalRequest('First Approval', PM1_EMAIL, 'additionalMessage1', 'ADDITIONAL_MESSAGE_1')
                }
            }
        }

        stage('Final Approval') {
            when {
                expression { params.deploy.toString() == "prod" }
            }
            steps {
                script {
                    def previousMessages = """
                        <li><strong>First Approval:</strong><br>${env.ADDITIONAL_MESSAGE_1}</li>
                    """
                    sendApprovalRequest('Final Approval', ADMIN_EMAIL, 'additionalMessageFinal', 'ADDITIONAL_MESSAGE_FINAL', previousMessages)

                    emailext (
                        subject: "Production Approval Confirmation",
                        body: """
                            <html>
                                <body>
                                    <h2>Production Approval Confirmation</h2>
                                    <p>All approvals have been received. Below are the details:</p>
                                    <ul>
                                        <li>First Approval: ${env.ADDITIONAL_MESSAGE_1}</li>
                                        <li>Final Approval: ${env.ADDITIONAL_MESSAGE_FINAL}</li>
                                    </ul>
                                </body>
                            </html>
                        """,
                        mimeType: 'text/html',
                        to: "${ADMIN_EMAIL}"
                    )
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    if (params.deploy.toString() == "prod") {
                        input message: "Deploy to production?", ok: "Deploy"
                    }
                    sh "docker build -t gcr.io/${GCP_PROJECT_ID}/${REPO_NAME}:${DOCKER_TAG} ."
                }
            }
        }

        stage('Push to GCR') {
            steps {
                script {
                    sh "docker image ls"
                    sh "gcloud auth configure-docker"
                    sh "docker push gcr.io/${GCP_PROJECT_ID}/${REPO_NAME}:${DOCKER_TAG}"
                }
            }
        }
    }
}

def sendApprovalRequest(stageName, approverEmail, messageName, additionalMessageEnvVar, previousMessages = '') {
    emailext (
        subject: "Approval Request - ${stageName}",
        body: """
            <html>
                <body>
                    <h2>Approval Request - ${stageName}</h2>
                    <p>${stageName} is required. Please review the changes and approve the job by clicking the link below:</p>
                    <a href="${JOB_URL}${BUILD_NUMBER}/input/">Approve Job</a>
                </body>
            </html>
        """,
        mimeType: 'text/html',
        to: approverEmail
    )

    def approval = input message: "${stageName}", parameters: [
        text(defaultValue: '', description: "Additional Message from ${stageName}", name: messageName)
    ]

    env[additionalMessageEnvVar] = approval
}
