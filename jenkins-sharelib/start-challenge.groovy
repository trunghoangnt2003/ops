pipeline {
  agent {
    kubernetes {
      yaml '''
        apiVersion: v1
        kind: Pod
        spec:
          serviceAccountName: jenkins-sa
          containers:
            - name: kubectl-cli-image
              image: quachuoiscontainer/kubectl-cli:v0.0.3
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
        '''
    }
  }

  parameters {
    string(name: 'APP_NAME', description: 'Application name')
    string(name: 'SERVICE_PORT', description: 'Service port')
    string(name: 'CONTAINER_PORT', description: 'Container port')
    string(name: 'NODE_PORT', description: 'Node port')
    string(name: 'REPLICA_COUNT', description: 'Number of replicas')
    string(name: 'CONTAINER_IMAGE', description: 'Container image')
    string(name: 'MEMORY_LIMIT', description: 'Memory limit')
    string(name: 'CPU_LIMIT', description: 'CPU limit')
    string(name: 'CPU_REQUEST', description: 'CPU request')
    string(name: 'MEMORY_REQUEST', description: 'Memory request')
  }

  stages {
    stage('Check Workspace') {
      steps {
        container('kubectl-cli-image') {
          sh '''#!/bin/bash
          echo "=== Checking Jenkins Agent Workspace ==="
          echo "Current directory: $(pwd)"
          echo "Testing kubectl connection:"
          kubectl top nodes || { echo "❌ Cannot connect to cluster"; exit 1; }
          '''
        }
      }
    }

    stage('Deploy Challenge') {
      steps {
        container('kubectl-cli-image') {
          sh '''#!/bin/bash
          set -e
          echo "=== Deploying Challenge with direct parameters ==="

          git clone https://github.com/fctf-git-repo/challenge-config.git
          cd challenge-config/websecpro_chilp-1

          # Export parameters as environment variables
          export APP_NAME="${APP_NAME}"
          export SERVICE_PORT="${SERVICE_PORT}"
          export CONTAINER_PORT="${CONTAINER_PORT}"
          export NODE_PORT="${NODE_PORT}"
          export REPLICA_COUNT="${REPLICA_COUNT}"
          export CONTAINER_IMAGE="${CONTAINER_IMAGE}"
          export MEMORY_LIMIT="${MEMORY_LIMIT}"
          export CPU_LIMIT="${CPU_LIMIT}"
          export CPU_REQUEST="${CPU_REQUEST}"
          export MEMORY_REQUEST="${MEMORY_REQUEST}"

          # Apply manifest bằng envsubst (non-blocking)
          envsubst < challenge.yaml | kubectl apply -f -

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
