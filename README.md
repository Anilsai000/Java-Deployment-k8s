# Spring Boot Application with DevOps Pipeline

This is a demo project showcasing a complete DevOps pipeline using:
- Java/Spring Boot
- Maven
- SonarQube
- Nexus
- Jenkins
- Docker
- Kubernetes (EKS)

## Prerequisites

1. AWS Account with appropriate permissions
2. CentOS 9 server (t2.large or better recommended) with:
   - Java 11 installed
   - Docker installed
   - AWS CLI installed
   - kubectl installed

## Jenkins Server Setup (CentOS 9)

### 1. Install Java 11
```bash
# Install Java 11
sudo dnf install java-11-openjdk java-11-openjdk-devel -y

# Verify Java installation
java -version
```

### 2. Install Jenkins
```bash
# Add Jenkins repository
sudo dnf install wget -y
sudo wget -O /etc/yum.repos.d/jenkins.repo https://pkg.jenkins.io/redhat-stable/jenkins.repo
sudo rpm --import https://pkg.jenkins.io/redhat-stable/jenkins.io-2023.key

# Install Jenkins
sudo dnf install jenkins -y

# Start Jenkins service
sudo systemctl start jenkins
sudo systemctl enable jenkins

# Get initial admin password
sudo cat /var/lib/jenkins/secrets/initialAdminPassword

# Jenkins will be available at http://your-server:8080
```

### 3. Install Docker
```bash
# Install Docker repository
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

# Install Docker
sudo dnf install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

# Start Docker service
sudo systemctl start docker
sudo systemctl enable docker

# Add jenkins user to docker group
sudo usermod -aG docker jenkins
```

### 4. Install Maven
```bash
# Install Maven
sudo dnf install maven -y

# Verify installation
mvn --version
```

### 5. Install SonarQube
```bash
# Install required packages
sudo dnf install unzip -y

# Create SonarQube user
sudo useradd -r -m -U -d /opt/sonarqube -s /bin/bash sonarqube

# Download and install SonarQube
sudo mkdir /opt/sonarqube
cd /opt/sonarqube
sudo wget https://binaries.sonarsource.com/Distribution/sonarqube/sonarqube-9.9.0.65466.zip
sudo unzip sonarqube-9.9.0.65466.zip
sudo mv sonarqube-9.9.0.65466/* .
sudo rm -rf sonarqube-9.9.0.65466 sonarqube-9.9.0.65466.zip

# Set permissions
sudo chown -R sonarqube:sonarqube /opt/sonarqube

# Configure system limits
sudo bash -c 'cat << EOF > /etc/sysctl.d/99-sonarqube.conf
vm.max_map_count=262144
fs.file-max=65536
EOF'

sudo bash -c 'cat << EOF > /etc/security/limits.d/99-sonarqube.conf
sonarqube   -   nofile   65536
sonarqube   -   nproc    4096
EOF'

sudo sysctl -p /etc/sysctl.d/99-sonarqube.conf

# Create systemd service
sudo bash -c 'cat << EOF > /etc/systemd/system/sonarqube.service
[Unit]
Description=SonarQube service
After=syslog.target network.target

[Service]
Type=simple
User=sonarqube
Group=sonarqube
PermissionsStartOnly=true
ExecStart=/opt/sonarqube/bin/linux-x86-64/sonar.sh start
ExecStop=/opt/sonarqube/bin/linux-x86-64/sonar.sh stop
StandardOutput=syslog
LimitNOFILE=65536
LimitNPROC=4096
TimeoutStartSec=5
Restart=always

[Install]
WantedBy=multi-user.target
EOF'

# Start SonarQube
sudo systemctl daemon-reload
sudo systemctl start sonarqube
sudo systemctl enable sonarqube

# SonarQube will be available at http://your-server:9000
# Default credentials: admin/admin
```

### 6. Install Nexus
```bash
# Create Nexus user
sudo useradd -r -m -U -d /opt/nexus -s /bin/bash nexus

# Download and install Nexus
sudo mkdir /opt/nexus
cd /opt/nexus
sudo wget https://download.sonatype.com/nexus/3/latest-unix.tar.gz
sudo tar -xvf latest-unix.tar.gz
sudo rm latest-unix.tar.gz
sudo mv nexus-3* nexus
sudo mv sonatype-work nexusdata

# Set permissions
sudo chown -R nexus:nexus /opt/nexus

# Create systemd service
sudo bash -c 'cat << EOF > /etc/systemd/system/nexus.service
[Unit]
Description=Nexus service
After=network.target

[Service]
Type=forking
LimitNOFILE=65536
ExecStart=/opt/nexus/nexus/bin/nexus start
ExecStop=/opt/nexus/nexus/bin/nexus stop
User=nexus
Group=nexus
Restart=always

[Install]
WantedBy=multi-user.target
EOF'

# Configure Nexus
sudo bash -c 'echo "run_as_user=\"nexus\"" > /opt/nexus/nexus/bin/nexus.rc'

# Start Nexus
sudo systemctl daemon-reload
sudo systemctl start nexus
sudo systemctl enable nexus

# Nexus will be available at http://your-server:8081
# Initial admin password can be found at:
sudo cat /opt/nexus/nexusdata/admin.password
```

### 7. Install AWS CLI and kubectl
```bash
# Install AWS CLI
sudo dnf install unzip -y
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
rm -rf aws awscliv2.zip

# Install kubectl
sudo bash -c 'cat << EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://packages.cloud.google.com/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF'

sudo dnf install -y kubectl

# Install eksctl
curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp
sudo mv /tmp/eksctl /usr/local/bin
```

### 8. Configure Firewall
```bash
# Configure firewall for all services
sudo firewall-cmd --permanent --add-port=8080/tcp  # Jenkins
sudo firewall-cmd --permanent --add-port=8081/tcp  # Nexus
sudo firewall-cmd --permanent --add-port=9000/tcp  # SonarQube
sudo firewall-cmd --reload
```

### 9. SELinux Configuration (if enabled)
```bash
# Set SELinux to permissive mode if you encounter issues
sudo setenforce 0
sudo sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config
```

## Infrastructure Setup

### 1. EKS Cluster Setup

```bash
# Create EKS cluster with 1 master and 1 worker node
eksctl create cluster \\
    --name demo-cluster \\
    --region us-east-1 \\
    --nodegroup-name demo-nodes \\
    --node-type t3.medium \\
    --nodes 1 \\
    --nodes-min 1 \\
    --nodes-max 1 \\
    --managed
```

### 2. ECR Repository Setup

```bash
# Create ECR repository
aws ecr create-repository --repository-name demo-k8s-app --region us-east-1
```

### 3. Jenkins Credentials Setup

Add the following credentials in Jenkins:
- AWS credentials (aws-credentials)
- SonarQube token (generate from http://localhost:9000)
- EKS kubeconfig

## Application Details

- Simple Spring Boot application with REST endpoints
- Health check endpoint at `/health`
- Main application endpoint at `/`
- Containerized using Docker
- Deployed to EKS using Kubernetes manifests

## Pipeline Stages

1. **Checkout**: Retrieves code from SCM
2. **Build**: Builds the Java application using Maven
3. **SonarQube Analysis**: Performs code quality analysis
4. **Build and Push Docker Image**: Creates and pushes Docker image to ECR
5. **Deploy to EKS**: Deploys application to EKS cluster

## Local Development

```bash
# Build the application
mvn clean package

# Run the application locally
java -jar target/demo-k8s-app-1.0.0.jar

# Build Docker image locally
docker build -t demo-k8s-app .

# Run Docker container locally
docker run -p 8080:8080 demo-k8s-app
```

## Monitoring and Scaling

- Application has built-in health checks
- Kubernetes deployment includes resource limits and requests
- Service is exposed via LoadBalancer
- Application can be scaled by modifying replicas in deployment.yaml

## Important Notes

1. Update AWS_REGION in Jenkinsfile if using a different region
2. Ensure all credentials are properly configured in Jenkins
3. Make sure Jenkins has necessary AWS permissions
4. The Jenkins server should have at least 4GB RAM for running all services
5. Default ports used:
   - Jenkins: 8080
   - Nexus: 8081
   - SonarQube: 9000
6. SELinux and firewall configurations are crucial for CentOS 9
7. System requirements:
   - Minimum 4GB RAM (8GB recommended)
   - 2 CPUs minimum
   - 40GB disk space
   - CentOS 9 Stream 