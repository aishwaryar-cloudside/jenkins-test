
import groovy.json.JsonOutput

def call(Map params) {
    pipeline {
        agent any

        environment {
            WORKSPACE = params.account.toString()
            ADMIN_EMAIL = 'aishwarya.r@thecloudside.com'
        }

        stages {
            stage('prepare') {
                steps {
                    script {
                        echo "${env.BUILD_NUMBER}"
                        echo "${env.GIT_COMMIT}"
                        echo "${env.BRANCH_NAME}"
                        echo "${env.BUILD_TAG}"
                        echo "${env.JOB_NAME}"
                        echo "${env.WORKSPACE}"
                    }
                }
            }
            stage('TF Init') {
                steps {
                    script {
                        echo "Terraform init start......."
                        sh '''#!/bin/bash
                        terraform fmt -check
                        terraform init -input=false
                        '''
                    }
                }
            }
            stage('TF Plan') {
                steps {
                    script {
                        echo "Terraform plan start......."
                        if (env.BRANCH_NAME.contains("-prod")) {
                            echo "going for prod deployment......."
                            sh '''#!/bin/bash
                            terraform workspace new ''' + env.WORKSPACE + '''-prod || terraform workspace select ''' + env.WORKSPACE + '''-prod
                            terraform validate
                            terraform plan -var-file=variables/''' + env.WORKSPACE + '''/prod.tfvars
                            '''
                        } else {
                            sh '''#!/bin/bash
                            terraform workspace new ''' + env.WORKSPACE + ''' || terraform workspace select ''' + env.WORKSPACE + '''
                            terraform validate
                            terraform plan -var-file=variables/''' + env.WORKSPACE + '''/dev.tfvars
                            '''
                        }
                    }
                }
            }
            stage('Approval Request') {
                steps {
                    script {
                        // Send approval request via email with job URL
                        emailext(
                            subject: "Approval Required for Terraform Apply",
                            body: """
                                <html>
                                    <body>
                                        <p>Dear Team,</p>
                                        <p>An approval is required to proceed with the Terraform Apply stage.</p>
                                        <p>Details:</p>
                                        <ul>
                                            <li>Workspace: ${env.WORKSPACE}</li>
                                            <li>Branch: ${env.BRANCH_NAME}</li>
                                            <li>Build Number: ${env.BUILD_NUMBER}</li>
                                            <li>Job URL: <a href="${env.BUILD_URL}">${env.BUILD_URL}</a></li>
                                        </ul>
                                        <p>Please approve or reject this request in the Jenkins interface.</p>
                                        <p>Best regards,<br>Jenkins</p>
                                    </body>
                                </html>
                            """,
                            mimeType: 'text/html',
                            to: "${ADMIN_EMAIL}"
                        )

                        // Pause pipeline for manual approval
                        input message: "Approval required for Terraform Apply",
                            parameters: [
                                string(defaultValue: '', description: 'Provide approval message', name: 'APPROVAL_MESSAGE')
                            ]

                        // Store the approval message in an environment variable
                        env.APPROVAL_MESSAGE = params.APPROVAL_MESSAGE
                        echo "Approval granted with message: ${env.APPROVAL_MESSAGE}"
                    }
                }
            }
            stage('TF Apply') {
                steps {
                    script {
                        echo "Terraform Apply start......."
                        if (env.BRANCH_NAME.contains("-prod")) {
                            echo "going for prod deployment......."
                            sh '''#!/bin/bash
                            terraform workspace new ''' + env.WORKSPACE + '''-prod || terraform workspace select ''' + env.WORKSPACE + '''-prod
                            terraform validate
                            terraform apply -var-file=variables/''' + env.WORKSPACE + '''/prod.tfvars
                            '''
                        } else {
                            sh '''#!/bin/bash
                            terraform workspace new ''' + env.WORKSPACE + ''' || terraform workspace select ''' + env.WORKSPACE + '''
                            terraform validate
                            terraform apply -var-file=variables/''' + env.WORKSPACE + '''/dev.tfvars
                            '''
                        }
                    }
                }
            }
        }
    }
}
