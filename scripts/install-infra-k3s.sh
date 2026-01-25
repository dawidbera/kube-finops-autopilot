#!/bin/bash

# KubeFinOps Autopilot - Infrastructure Installation on K3s
# Installs Kafka, MongoDB, MinIO, Prometheus and Grafana using Helm.

set -e

# Ensure KUBECONFIG is set for k3s
if [ -f /etc/rancher/k3s/k3s.yaml ]; then
  export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
fi

NAMESPACE="kubefinops"
echo "🚀 Creating namespace: $NAMESPACE"
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# Add Helm Repositories
echo "📦 Adding Helm repositories..."
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# 1. Install Kafka (Lite)
echo "📡 Installing Kafka..."
helm upgrade --install infra-kafka bitnami/kafka -n $NAMESPACE \
  --set global.imageRegistry=public.ecr.aws \
  --set global.security.allowInsecureImages=true \
  --set persistence.enabled=false \
  --set zookeeper.persistence.enabled=false \
  --set replicaCount=1

# 2. Install MongoDB
echo "🍃 Installing MongoDB..."
helm upgrade --install infra-mongodb bitnami/mongodb -n $NAMESPACE \
  --set global.imageRegistry=public.ecr.aws \
  --set global.security.allowInsecureImages=true \
  --set auth.enabled=false \
  --set persistence.enabled=false

# 3. Install MinIO
echo "🗄️ Installing MinIO..."
helm upgrade --install infra-minio bitnami/minio -n $NAMESPACE \
  --set global.imageRegistry=public.ecr.aws \
  --set global.security.allowInsecureImages=true \
  --set auth.rootUser=admin \
  --set auth.rootPassword=password \
  --set persistence.enabled=false \
  --set service.type=ClusterIP



# 4. Install Prometheus & Grafana (Kube-Prometheus-Stack)
echo "📊 Installing Monitoring Stack..."
helm upgrade --install infra-monitoring prometheus-community/kube-prometheus-stack -n $NAMESPACE \
  --set grafana.enabled=true \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false

echo ""
echo "✅ Infrastructure installation triggered!"
echo "⏳ Wait for all pods to be READY in namespace: $NAMESPACE"
echo "👉 Use 'kubectl get pods -n $NAMESPACE' to monitor progress."
