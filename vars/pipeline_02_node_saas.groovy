import groovy.json.JsonOutput

def call(Map params) {
    pipeline {
        agent any
        environment {
            COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
            ECR_DOCKER_TAG = "v${env.BUILD_NUMBER}.0.0"
            GCP_DEFAULT_REGION = "asia-south1"
            GCP_REGISTRY = "asia-south1-docker.pkg.dev"
            PROJECT_ID = params.account.toString()
            GCP_DOCKER_TAG = "${params.name}-v${env.BUILD_NUMBER}.0.0"
            GCP_REPOSITORY = "${params.account}${params.deploy == 'prod' ? '-prod' : ''}"
            REPO_NAME = "${params.name}${params.deploy == 'prod' ? '-prod' : ''}"
            GCS_BUCKET = "bucket-application-files"
            
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
                                        <p><a href="${env.BUILD_URL}/input/" style="background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Please approve from Jenkins</a></p>
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
                        echo "Building Docker image: ${env.JOB_NAME}:${env.REPO_NAME}-v${env.BUILD_NUMBER}.0.0"
                        sh "docker build -t ${AWS_ECR_REGISTRY}/${AWS_ECR_REPOSITORY}:${REPO_NAME}-${ECR_DOCKER_TAG} ."
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
        }
    }
}

def sendApprovalRequest(stageName, approverEmail, approverUser, messageName, additionalMessageEnvVar, previousMessages = '') {
    emailext(
        subject: "Approval Request - ${stageName}",
        body: """
            <html>
                <body>
                    <h2>Approval Request - ${stageName}</h2>
                    <h3>Approval Messages:</h3>
                    <ul>${previousMessages}</ul>
                    <p>${stageName} is required. Please review the changes and approve the job by clicking the link below:</p>
              

                    <a href="${env.BUILD_URL}/input/">Approve Job</a>

                </body>
            </html>
        """,
        mimeType: 'text/html',
        to: approverEmail
    )
    def approval = input message: "${stageName}", parameters: [
        text(defaultValue: '', description: "Additional Message from ${stageName}", name: messageName)
    ], submitter: approverUser
    env[additionalMessageEnvVar] = approval
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
    env.AWS_ECR_REGISTRY = ecrRegistry
    env.ECR_DOCKER_TAG = params.name + "-v${env.BUILD_NUMBER}.0.0"
    env.AWS_ECR_REPOSITORY = params.account + (params.deploy == "prod" ? "-prod" : "")
}
