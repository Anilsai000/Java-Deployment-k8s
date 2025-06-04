pipeline {
    agent any

    environment {
        AWS_REGION = 'us-east-1'
        AWS_ACCOUNT_ID = '014498663222'
        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        IMAGE_NAME = 'demo-k8s-app'
        SONAR_TOKEN = credentials('sonar-token')
        KUBECONFIG = credentials('eks-kubeconfig')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Upload Artifact to Nexus') {
    steps {
        sh 'mvn deploy -DskipTests'
    }
}

        stage('SonarQube Analysis') {
            steps {
                sh 'mvn sonar:sonar -Dsonar.login=$SONAR_TOKEN'
            }
        }

        stage('Build and Push Docker Image') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'aws-creds',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {
                    sh """
                        aws configure set aws_access_key_id $AWS_ACCESS_KEY_ID
                        aws configure set aws_secret_access_key $AWS_SECRET_ACCESS_KEY
                        aws configure set region $AWS_REGION

                        echo "Logging in to ECR: ${ECR_REGISTRY}"
                        aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

                        docker build -t $IMAGE_NAME:$BUILD_NUMBER .
                        docker tag $IMAGE_NAME:$BUILD_NUMBER ${ECR_REGISTRY}/$IMAGE_NAME:$BUILD_NUMBER
                        docker push ${ECR_REGISTRY}/$IMAGE_NAME:$BUILD_NUMBER
                    """
                }
            }
        }

        stage('Deploy to EKS') {
            steps {
                script {
                    sh """
                        export KUBECONFIG=$KUBECONFIG
                        envsubst < k8s/deployment.yaml | kubectl apply -f -
                        kubectl apply -f k8s/service.yaml
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
