pipeline {
    agent {
        kubernetes {
            yaml """
                apiVersion: v1
                kind: Pod
                spec:
                    serviceAccountName: jenkins-sa
                    containers:
                        - name: kubectl-cli-image
                          image: quachuoiscontainer/kubectl-cli:v0.0.1
                          command:
                              - cat
                          tty: true
                          securityContext:
                              runAsUser: 0
                              runAsGroup: 0
                              runAsNonRoot: false
                              allowPrivilegeEscalation: true
                              privileged: true
                          imagePullPolicy: IfNotPresent
                          resources:
                              requests:
                                  memory: "256Mi"
                                  cpu: "100m"
                              limits:
                                  memory: "512Mi"
                                  cpu: "300m"
            """
        }
    }

    parameters {
        file(name: 'ENV_FILE', description: 'Upload your .env file for challenge deployment')
    }

    stages {
        stage('Check Workspace') {
            steps {
                container('kubectl-cli-image') {
                    sh '''#!/bin/bash
                        echo "=== Checking Jenkins Agent Workspace ==="
                        echo "Current directory: $(pwd)"
                        echo "Testing kubectl connection:"
                        kubectl cluster-info || { echo "❌ Cannot connect to cluster"; exit 1; }
                    '''
                }
            }
        }

        stage('Deploy Challenge') {
            steps {
                container('kubectl-cli-image') {
                    sh '''#!/bin/bash
                        set -e
                        echo "=== Deploying Challenge with .env ==="

                        git clone https://github.com/fctf-git-repo/challenge-config.git
                        cd challenge-config/websecpro_chilp-1

                        # Copy file parameter thành .env
                        cp "$ENV_FILE" .env

                        # Load biến từ file .env
                        export $(grep -v '^#' .env | xargs)

                        # Apply manifest bằng envsubst (non-blocking)
                        envsubst < challenge.yaml | kubectl apply -f - --wait=false

                        echo "✅ Challenge manifest submitted to cluster"
                    '''
                }
            }
        }

        stage('Verify Challenge') {
            steps {
                container('kubectl-cli-image') {
                    sh '''#!/bin/bash
                        set -e
                        echo "=== Verifying Challenge Deployment ==="

                        DEPLOY_NAME="${APP_NAME}-deployment"
                        SERVICE_NAME="${APP_NAME}-service"
                        INGRESS_NAME="${APP_NAME}"

                        # Kiểm tra Deployment
                        if kubectl get deployment $DEPLOY_NAME -n challenge >/dev/null 2>&1; then
                            echo "✅ Deployment $DEPLOY_NAME found"
                        else
                            echo "❌ Deployment $DEPLOY_NAME not found!"
                            exit 1
                        fi

                        # Kiểm tra Service
                        if kubectl get svc $SERVICE_NAME -n challenge >/dev/null 2>&1; then
                            echo "✅ Service $SERVICE_NAME found"
                        else
                            echo "❌ Service $SERVICE_NAME not found!"
                            exit 1
                        fi

                        # Kiểm tra Ingress nếu có
                        if kubectl get ingress $INGRESS_NAME -n challenge >/dev/null 2>&1; then
                            echo "✅ Ingress $INGRESS_NAME found"
                        else
                            echo "ℹ️ No ingress resource detected"
                        fi

                        echo "🎉 Verify stage completed successfully!"
                    '''
                }
            }
        }
    }
}