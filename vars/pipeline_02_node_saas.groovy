import groovy.json.JsonOutput

def call(Map params) {
    pipeline {
        agent any
        environment {
            COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
            ECR_DOCKER_TAG = "v${env.BUILD_NUMBER}.0.0"
            GCP_DEFAULT_REGION = "asia-south1"
            GCP_REGISTRY = "asia-south1-docker.pkg.dev"
            ACCOUNT = "${params.account}"
            REPO_NAME = "${params.name}" 
            PROJECT_ID = params.project_id.toString()
            GCP_DOCKER_TAG = "${params.name}-v${env.BUILD_NUMBER}.0.0"
            GCP_REPOSITORY = "${params.account}${params.deploy == 'prod' ? '-prod' : ''}"
            GCS_BUCKET = "bucket-application-files"
            ENVIRONMENT = params.deploy.toString()

            PM1_EMAIL = 'aishwarya.r@thecloudside.com'
            PM2_EMAIL = 'aishwarya.r@thecloudside.com'
            ADMIN_EMAIL = 'aishwarya.r@thecloudside.com'

            JOB_URL = "${env.JENKINS_URL}job/${env.JOB_NAME}/"
            PM1_USER = 'Aishu'
            PM2_USER = 'Aishu'
            ADMIN_USER = 'admin'
        }
        stages {
            stage('Prepare') {
                steps {
                    script {
                        setEnvironmentVariables(params)

                        echo "BUILD_NUMBER: ${env.BUILD_NUMBER}"
                        echo "COMMIT_ID: ${env.COMMIT_ID}"
                        echo "BRANCH_NAME: ${env.BRANCH_NAME}"
                        echo "BUILD_TAG: ${env.BUILD_TAG}"
                        echo "JOB_NAME: ${env.JOB_NAME}"
                        echo "Commit >> Project: ${env.COMMIT_ID}"
                        echo "${env.BUILD_URL}"
                        sh "printenv"
                    }
                }
                post {
                    always {
                        script {
                            echo 'Post build step'
                        }
                        bitbucketStatusNotify(buildState: 'INPROGRESS', repoSlug: env.REPO_NAME, commitId: env.COMMIT_ID)
                    }
                    failure {
                        script {
                            echo 'Send email on failure'
                        }
                        bitbucketStatusNotify(buildState: 'FAILED', repoSlug: env.REPO_NAME, commitId: env.COMMIT_ID)
                    }
                }
            }
            stage('First Approval') {
                when {
                    expression { params.deploy.toString() == "prod" }
                }
                steps {
                    script {
                        sendApprovalRequest('First Approval', PM1_EMAIL, PM1_USER, 'additionalMessage1', 'ADDITIONAL_MESSAGE_1')
                    }
                }
            }
            stage('Second Approval') {
                when {
                    expression { params.deploy.toString() == "prod" }
                }
                steps {
                    script {
                        def firstApprovalMessage = "<li><strong>First Approval:</strong><br>${env.ADDITIONAL_MESSAGE_1}</li>"
                        sendApprovalRequest('Second Approval', PM2_EMAIL, PM2_USER, 'additionalMessage2', 'ADDITIONAL_MESSAGE_2', firstApprovalMessage)
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
                            <li><strong>Second Approval:</strong><br>${env.ADDITIONAL_MESSAGE_2}</li>
                        """
                        sendApprovalRequest('Final Approval', ADMIN_EMAIL, ADMIN_USER, 'additionalMessageFinal', 'ADDITIONAL_MESSAGE_FINAL', previousMessages)

                        echo "Manager approved: ${env.ADDITIONAL_MESSAGE_FINAL}"
                        emailext(
                            subject: "Production Approval Confirmation",
                            body: """
                                <html>
                                    <body style="font-family: Arial, sans-serif; line-height: 1.6;">
                                        <h2>Production Approval Confirmation</h2>
                                        <p>All approvals have been received. Below are the details:</p>
                                        <h3>Approval Messages:</h3>
                                        <ul>
                                            <li><strong>First Approval:</strong><br>${env.ADDITIONAL_MESSAGE_1}</li>
                                            <li><strong>Second Approval:</strong><br>${env.ADDITIONAL_MESSAGE_2}</li>
                                            <li><strong>Final Approval:</strong><br>${env.ADDITIONAL_MESSAGE_FINAL}</li>
                                        </ul>
                                        <h3>Jenkins UI:</h3>
                                        <p><a href="${env.BUILD_URL}" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Please approve from Jenkins</a></p>
                                    </body>
                                </html>
                            """,
                            mimeType: 'text/html',
                            to: ADMIN_EMAIL
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
                        echo "Building Docker image: ${env.REPO_NAME}:${env.GCP_DOCKER_TAG}"
                        sh "docker build -t ${GCP_REGISTRY}/${PROJECT_ID}/${GCP_REPOSITORY}/${REPO_NAME}:${GCP_DOCKER_TAG} ."
                    }
                }
                post {
                    always {
                        script {
                            echo 'Post build step'
                        }
                    }
                    failure {
                        script {
                            echo 'Send email on failure'
                        }
                        bitbucketStatusNotify(buildState: 'FAILED', repoSlug: env.REPO_NAME, commitId: env.COMMIT_ID)
                    }
                }
            }
            stage('Push to Artifact') {
                steps {
                    script {
                        sh "docker image ls"
                        sh '''
                            #!/bin/bash
                            gcloud auth configure-docker asia-south1-docker.pkg.dev
                            docker push ${GCP_REGISTRY}/${PROJECT_ID}/${GCP_REPOSITORY}/${REPO_NAME}:${GCP_DOCKER_TAG}
                        '''
                    }
                }
                post {
                    always {
                        script {
                            echo 'Post artifact push step'
                        }
                    }
                    failure {
                        script {
                            echo 'Send email on push failure'
                        }
                        bitbucketStatusNotify(buildState: 'FAILED', repoSlug: env.REPO_NAME, commitId: env.COMMIT_ID)
                    }
                }
            }
            stage('Deployed') {
                steps {
                    script {
                        sh '''
                        #!/bin/bash
                        gsutil cp gs://${GCS_BUCKET}/${PROJECT_ID}/${ACCOUNT}/${REPO_NAME}/deployment.yaml .

                        echo "GCP_REGISTRY=${GCP_REGISTRY}"
                        echo "PROJECT_ID=${PROJECT_ID}"
                        echo "GCP_REPOSITORY=${GCP_REPOSITORY}"
                        echo "REPO_NAME=${REPO_NAME}"
                        echo "GCP_DOCKER_TAG=${GCP_DOCKER_TAG}"
                        replacement="image: ${GCP_REGISTRY}/${PROJECT_ID}/${GCP_REPOSITORY}/${REPO_NAME}:${GCP_DOCKER_TAG}"
                        echo "Replacement string: $replacement"

            # Use sed to replace the line
                        sed -i "s|image:.*|$replacement|" deployment.yaml
                        
                        mv deployment.yaml deployment-${BUILD_NUMBER}.yaml
                        if [ "${params.deploy}" == "prod" ]; then
                            gcloud container clusters get-credentials ooredoo-powerplay-gke-prod-reg-as1 --region asia-south1 --project ${PROJECT_ID} --dns-endpoint
                        else
                            gcloud container clusters get-credentials ooredoo-powerplay-gke-dev-reg-as1 --region asia-south1 --project ${PROJECT_ID} --dns-endpoint
                        fi
                        kubectl apply -f deployment-${BUILD_NUMBER}.yaml
                        gsutil mv deployment-${BUILD_NUMBER}.yaml gs://${GCS_BUCKET}/${PROJECT_ID}/${REPO_NAME}/
                        '''
                    }
                }
                post {
                    success {
                        script {
                            echo 'Post deployment step'
                        }
                        bitbucketStatusNotify(buildState: 'SUCCESSFUL', repoSlug: env.REPO_NAME, commitId: env.COMMIT_ID)
                    }
                    failure {
                        script {
                            echo 'Send email on failure'
                        }
                        bitbucketStatusNotify(buildState: 'FAILED', repoSlug: env.REPO_NAME, commitId: env.COMMIT_ID)
                    }
                }
            }
        }
    }
}


def setEnvironmentVariables(Map params) {
    if (params.account == "fantasy-sports") {
        configureEnvironment(params, "761018859673.dkr.ecr.ap-south-1.amazonaws.com")
    } else if (params.account == "saas") {
        configureEnvironment(params, "528757811540.dkr.ecr.ap-south-1.amazonaws.com")
    }
}

def configureEnvironment(Map params, String ecrRegistry) {
    env.WORKSPACE = params.account
    env.REPO_NAME = params.name + (params.deploy == "prod" ? "-prod" : "")
    env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
    env.GCP_REGISTRY = 'asia-south1-docker.pkg.dev'
    env.GCP_DOCKER_TAG = "${params.name}-v${env.BUILD_NUMBER}.0.0"
    env.GCP_REPOSITORY = "${params.account}${params.deploy == "prod" ? "-prod" : ""}"
}
def sendApprovalRequest(approvalType, email, user, messageVar, envVar, additionalMessages = '') {
    input message: "${approvalType} required by ${user}.",
        parameters: [
            string(defaultValue: '', description: 'Enter approval message', name: messageVar)
        ]
    env[envVar] = messageVar
    echo "Approval received from ${user} with message: ${env[envVar]}"
    emailext(
        subject: "${approvalType} Request",
        body: """
            <html>
                <body>
                    <p>Approval request for ${approvalType}.</p>
                    <p>${additionalMessages}</p>
                    <p>Approval message: ${env[envVar]}</p>
                    <p>Please review and respond.</p>
                </body>
            </html>
        """,
        mimeType: 'text/html',
        to: email
    )
}
