# Hướng dẫn cài đặt FCTF K3s

Tài liệu này hướng dẫn cài đặt hệ thống FCTF K3s trên 2 môi trường:
- **Option 1:** Production trên Google Cloud VM
- **Option 2:** Development/Testing trên VirtualBox Local

---

## Yêu cầu

### Yêu cầu chung
- **OS:** Ubuntu 20.04+ hoặc Ubuntu 22.04 LTS (khuyến nghị)
- **RAM:** Tối thiểu 8GB (Production khuyến nghị 16GB)
- **CPU:** 4 cores trở lên
- **Disk:** 50GB+ (Production khuyến nghị 100GB+)
- **User:** Root access hoặc sudo privileges

### Option 1: Production - Google Cloud VM
- **Machine Type:** e2-standard-4 hoặc cao hơn (4 vCPU, 16GB RAM)
- **Network:** Public IP với firewall cho ports: 80, 443, 6443, 2049, 111
- **Domain:** Có domain *.fctf.cloud đã trỏ về server

### Option 2: Development - VirtualBox Local
- **Host OS:** Windows 10/11, macOS, hoặc Linux
- **VirtualBox:** Version 7.0+
- **Network:** NAT adapter với port forwarding hoặc Host-only adapter

---

## Các bước cài đặt

### 1. Chuẩn bị server

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Cài đặt các công cụ cần thiết
sudo apt install -y curl wget git nano vim

# Cấu hình timezone
sudo timedatectl set-timezone Asia/Ho_Chi_Minh
```

### 2. Cài đặt K3s Master Node

**Cho Production (Google Cloud):**
```bash
# Cài K3s với domain TLS SAN
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --disable traefik --kubelet-arg=config=/etc/rancher/k3s/kubelet.config --write-kubeconfig-mode 644 --tls-san kubeconfig.fctf.cloud" sh -
```

**Cho Development (VirtualBox):**
```bash
# Cài K3s đơn giản hơn, không cần domain
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --disable traefik --write-kubeconfig-mode 644" sh -
```

**Kiểm tra và cấu hình kubectl:**
```bash
# Kiểm tra K3s đã chạy
sudo systemctl status k3s

# Cấu hình kubectl
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config
export KUBECONFIG=~/.kube/config

# Thêm vào ~/.bashrc để tự động load
echo 'export KUBECONFIG=~/.kube/config' >> ~/.bashrc

# Kiểm tra cluster
kubectl get nodes
```

### 3. Cài đặt Helm

```bash
# Cài đặt Helm 3
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Kiểm tra
helm version
```

### 4. Cài đặt NFS Server

```bash
# Cài đặt NFS server
sudo apt update
sudo apt install -y nfs-kernel-server nfs-common

# Tạo thư mục share
sudo mkdir -p /srv/nfs/share
sudo chown nobody:nogroup /srv/nfs/share
sudo chmod 777 /srv/nfs/share

# Cấu hình exports
echo "/srv/nfs/share *(rw,sync,no_subtree_check,no_root_squash,insecure)" | sudo tee -a /etc/exports

# Apply cấu hình
sudo exportfs -ra
sudo systemctl enable nfs-kernel-server
sudo systemctl restart nfs-kernel-server

# Kiểm tra
showmount -e localhost
sudo exportfs -v
```

### 5. Clone repository

```bash
cd ~
git clone https://github.com/fctf-git-repo/FCTF-k3s-manifest.git
cd FCTF-k3s-manifest
```

### 6. Cài đặt các thành phần qua Helm

```bash
# Chạy script cài đặt tự động
bash helm.sh
```

**Hoặc cài đặt từng bước:**
# có thể vào ./helm.sh để cài từng bước bắt đầu từ # Apply helm repos

### 7. Deploy ứng dụng CTF

```bash
# Apply ConfigMaps và Secrets
kubectl apply -f ./prod/env/configmap/
kubectl apply -f ./prod/env/secret/

# Deploy applications
kubectl apply -f ./prod/app/admin-mvc/
kubectl apply -f ./prod/app/contestant-be/
kubectl apply -f ./prod/app/contestant-portal/
kubectl apply -f ./prod/app/deployment-center/

# Apply Service Accounts
kubectl apply -f ./prod/sa/argo-workflow/
kubectl apply -f ./prod/sa/jenkins/

# Apply Ingress
kubectl apply -f ./prod/ingress/certificate/
kubectl apply -f ./prod/ingress/nginx/
```

### 8. Kiểm tra cài đặt

```bash
# Kiểm tra tất cả pods
kubectl get pods -A

# Kiểm tra services
kubectl get svc -A

# Kiểm tra ingress
kubectl get ingress -A

# Kiểm tra PV/PVC
kubectl get pv
kubectl get pvc -A

# Kiểm tra NFS mount
df -h | grep nfs
```

## Truy cập các dịch vụ

### Production (Google Cloud)

#### Các ứng dụng CTF
- **Admin Portal:** https://admin.fctf.cloud
- **Contestant Portal:** https://contestant.fctf.cloud
- **API Backend:** https://api.fctf.cloud/contestant-be/

#### Monitoring & Tools
- **Filebrowser:** https://filebrowser-zmn0zjiwmju.fctf.cloud (admin/admin)
- **Grafana:** https://grafana.fctf.cloud
- **Argo Workflows:** https://argo.fctf.cloud
- **Kubernetes Dashboard:** https://kubernetes-dashboard.fctf.cloud

#### Lấy credentials

**Grafana:**
```bash
kubectl get secret -n monitoring prometheus-grafana -o jsonpath="{.data.admin-password}" | base64 -d
echo
# Username: admin
```

**Kubernetes Dashboard:**
```bash
# Tạo service account
kubectl apply -f - <<EOF
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
- kind: ServiceAccount
  name: admin-user
  namespace: kubernetes-dashboard
EOF

# Lấy token
kubectl -n kubernetes-dashboard create token admin-user
```

### Development (VirtualBox)

> **Lưu ý:** Ở môi trường local, ingress domain không hoạt động. Có 3 cách để truy cập services:

#### Cách 1: Port Forwarding (Khuyến nghị - Dễ nhất)

**Cách nhanh - Dùng script tự động:**
```bash
# SSH vào VM
ssh -p 2222 fctf@localhost

# Chạy script port-forward tất cả services
cd ~/FCTF-k3s-manifest
chmod +x local-port-forward.sh
bash local-port-forward.sh

# Script sẽ tự động forward tất cả services
# Nhấn Ctrl+C để dừng tất cả
```

**Hoặc forward từng service thủ công:**
```bash
# Filebrowser
kubectl port-forward -n storage svc/filebrowser 8080:80 --address 0.0.0.0

# Grafana (terminal khác)
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80 --address 0.0.0.0

# Argo Workflows (terminal khác)
kubectl port-forward -n argo svc/argo-workflows-server 2746:2746 --address 0.0.0.0
```

**Truy cập từ host Windows:**
- **Admin Portal:** http://localhost:8000
- **Contestant Portal:** http://localhost:5173
- **Contestant API:** http://localhost:5010
- **Deployment Center API:** http://localhost:5020
- **Filebrowser:** http://localhost:8080
- **Grafana:** http://localhost:3000
- **Argo Workflows:** https://localhost:2746

#### Cách 2: NodePort Services

**Tạo file `local-nodeport-services.yaml`:**
```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: filebrowser-nodeport
  namespace: storage
spec:
  type: NodePort
  selector:
    app.kubernetes.io/name: filebrowser
  ports:
    - port: 80
      targetPort: 8080
      nodePort: 30080
      protocol: TCP
---
apiVersion: v1
kind: Service
metadata:
  name: grafana-nodeport
  namespace: monitoring
spec:
  type: NodePort
  selector:
    app.kubernetes.io/name: grafana
  ports:
    - port: 80
      targetPort: 3000
      nodePort: 30300
      protocol: TCP
---
apiVersion: v1
kind: Service
metadata:
  name: argo-nodeport
  namespace: argo
spec:
  type: NodePort
  selector:
    app.kubernetes.io/name: argo-workflows-server
  ports:
    - port: 2746
      targetPort: 2746
      nodePort: 32746
      protocol: TCP
```

**Apply NodePort services:**
```bash
kubectl apply -f local-nodeport-services.yaml

# Kiểm tra
kubectl get svc -n storage filebrowser-nodeport
kubectl get svc -n monitoring grafana-nodeport
kubectl get svc -n argo argo-nodeport
```

**Truy cập từ host Windows:**
- Filebrowser: http://localhost:30080
- Grafana: http://localhost:30300
- Argo: https://localhost:32746

#### Cách 3: Ingress với Host-only Network

**Chỉ dùng nếu bạn muốn test ingress như production.**

**Bước 1: Lấy IP của VM (Host-only adapter):**
```bash
# Trong VM
ip addr show enp0s8  # hoặc eth1
# Ví dụ: 192.168.56.101
```

**Bước 2: Cấu hình hosts file trên Windows:**
```powershell
# C:\Windows\System32\drivers\etc\hosts
192.168.56.101 filebrowser.fctf.local
192.168.56.101 grafana.fctf.local
192.168.56.101 argo.fctf.local
```

**Bước 3: Sửa ingress để dùng domain local:**
```bash
# Sửa các file ingress trong prod/ingress/nginx/
# Thay domain *.fctf.cloud thành *.fctf.local
```

**Bước 4: Truy cập:**
- http://filebrowser.fctf.local
- http://grafana.fctf.local
- http://argo.fctf.local

---

#### Thông tin đăng nhập

**Filebrowser**
- Username: `admin`
- Password: `admin`

#### Grafana (Monitoring)
```bash
# Lấy password
kubectl get secret -n monitoring prometheus-grafana -o jsonpath="{.data.admin-password}" | base64 -d
echo
# Username: admin
```

## Xử lý sự cố

### NFS mount failed
```bash
# Kiểm tra NFS server
sudo systemctl status nfs-kernel-server
showmount -e localhost

# Kiểm tra exports
sudo exportfs -v

# Restart NFS
sudo systemctl restart nfs-kernel-server

# Xóa pod để recreate
kubectl delete pod -n storage -l app.kubernetes.io/name=filebrowser
```

### Pod pending hoặc CrashLoopBackOff
```bash
# Xem logs
kubectl logs -n <namespace> <pod-name>

# Xem events
kubectl describe pod -n <namespace> <pod-name>

# Xem tất cả events
kubectl get events -A --sort-by='.lastTimestamp'
```

### Certificate không được issue
```bash
# Kiểm tra cert-manager
kubectl get pods -n cert-manager

# Xem logs cert-manager
kubectl logs -n cert-manager -l app=cert-manager

# Kiểm tra certificate
kubectl get certificate -A
kubectl describe certificate -n <namespace> <cert-name>
```

## Backup và Restore

### Backup NFS data
```bash
# Backup toàn bộ NFS share
sudo tar -czf nfs-backup-$(date +%Y%m%d).tar.gz /srv/nfs/share

# Restore
sudo tar -xzf nfs-backup-YYYYMMDD.tar.gz -C /
```

### Backup K3s
```bash
# Backup K3s datastore
sudo systemctl stop k3s
sudo tar -czf k3s-backup-$(date +%Y%m%d).tar.gz /var/lib/rancher/k3s
sudo systemctl start k3s
```

## Uninstall

### Xóa từng component
```bash
# Xóa helm releases
helm uninstall filebrowser -n storage
helm uninstall argo-workflows -n argo
helm uninstall prometheus -n monitoring
helm uninstall loki-stack -n monitoring
helm uninstall redis -n db
helm uninstall mariadb -n db
helm uninstall cert-manager -n cert-manager
helm uninstall ingress-nginx -n ingress-nginx

# Xóa namespaces
kubectl delete namespace storage argo monitoring db cert-manager ingress-nginx
```

### Gỡ K3s hoàn toàn
```bash
/usr/local/bin/k3s-uninstall.sh
```

### Gỡ NFS server
```bash
sudo systemctl stop nfs-kernel-server
sudo apt remove --purge -y nfs-kernel-server nfs-common
sudo rm -rf /srv/nfs
```

## Tài liệu tham khảo

- [K3s Documentation](https://docs.k3s.io/)
- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [NFS Server Setup](https://ubuntu.com/server/docs/service-nfs)
