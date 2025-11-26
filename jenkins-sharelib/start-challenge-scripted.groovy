properties([
  parameters([
    string(name: 'APP_NAME', description: 'Application name'),
    string(name: 'SERVICE_PORT', description: 'Service port'),
    string(name: 'CONTAINER_PORT', description: 'Container port'),
    string(name: 'NODE_PORT', description: 'Node port'),
    string(name: 'REPLICA_COUNT', description: 'Number of replicas'),
    string(name: 'CONTAINER_IMAGE', description: 'Container image'),
    string(name: 'MEMORY_LIMIT', description: 'Memory limit'),
    string(name: 'CPU_LIMIT', description: 'CPU limit'),
    string(name: 'CPU_REQUEST', description: 'CPU request'),
    string(name: 'MEMORY_REQUEST', description: 'Memory request')
  ])
])

def label = "kubectl-agent-${UUID.randomUUID().toString()}"

podTemplate(
  label: label,
  serviceAccount: 'jenkins-sa',
  containers: [
    containerTemplate(
      name: 'kubectl',
      image: 'quachuoiscontainer/kubectl-cli:v0.0.3',
      command: 'cat',
      ttyEnabled: 'true',
      privileged: 'true',
      runAsUser: '0',
      resourceRequestMemory: '256Mi',
      resourceRequestCpu: '100m',
      resourceLimitMemory: '512Mi',
      resourceLimitCpu: '300m'
    )
  ]
) {
  node(label) {
    stage('Check Workspace') {
      container('kubectl') {
        sh '''
          echo "=== Checking Jenkins Agent Workspace ==="
          echo "Current directory: $(pwd)"
          echo "Testing kubectl connection:"
          kubectl top nodes || { echo "❌ Cannot connect to cluster"; exit 1; }
        '''
      }
    }

    stage('Deploy Challenge') {
      container('kubectl') {
        sh """
          set -e
          echo "=== Deploying Challenge for $APP_NAME ==="

          git clone https://github.com/fctf-git-repo/challenge-config.git
          cd challenge-config/websecpro_chilp-1

          envsubst < challenge.yaml | kubectl apply -f -

          echo "✅ Challenge manifest submitted to cluster"
        """
      }
    }

    stage('Verify Challenge') {
      container('kubectl') {
        sh """
          set -e
          echo "=== Verifying Challenge Deployment ==="

          DEPLOY_NAME="${APP_NAME}-deployment"
          SERVICE_NAME="${APP_NAME}-service"
          INGRESS_NAME="${APP_NAME}"

          kubectl get deployment $DEPLOY_NAME -n challenge >/dev/null && echo "✅ Deployment found" || (echo "❌ Deployment missing"; exit 1)
          kubectl get svc $SERVICE_NAME -n challenge >/dev/null && echo "✅ Service found" || (echo "❌ Service missing"; exit 1)
          kubectl get ingress $INGRESS_NAME -n challenge >/dev/null && echo "✅ Ingress found" || echo "ℹ️ No ingress resource"
        """
      }
    }
  }
}
