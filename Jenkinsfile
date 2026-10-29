pipeline {
    agent any

    environment {
        AWS_REGION = 'ap-southeast-2'
        AWS_ACCOUNT_ID = '684905329064'
        ECR_REPOSITORY = 'javatechflow/aws-practices'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/devJavaProject96/spring-boot-microservices.git'
            }
        }

        stage('Check Tools') {
            steps {
                sh '''
                    java -version
                    mvn -version
                    docker --version
                    aws --version
                    aws sts get-caller-identity
                '''
            }
        }

        stage('Build and Test') {
            steps {
                sh '''
                    set -e

                    for SERVICE in \
                        APIGateway AdminServer ConfigServer \
                        NotificationServices OrderServices PaymentServices \
                        ProductService ServiceRegistry ZipkinService
                    do
                        echo "Building $SERVICE"
                        mvn -B -f "$SERVICE/pom.xml" clean verify
                    done
                '''
            }
        }

        stage('Login to ECR') {
            steps {
                sh '''
                    aws ecr get-login-password \
                      --region "$AWS_REGION" |
                    docker login \
                      --username AWS \
                      --password-stdin \
                      "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
                '''
            }
        }

        stage('Build and Push Docker Images') {
            steps {
                sh '''
                    set -e

                    ECR_URI="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"
                    IMAGE_TAG=$(git rev-parse --short HEAD)

                    for SERVICE in \
                        APIGateway AdminServer ConfigServer \
                        NotificationServices OrderServices PaymentServices \
                        ProductService ServiceRegistry ZipkinService
                    do
                        echo "Building Docker image for $SERVICE"

                        docker build \
                          -t "$ECR_URI:$SERVICE-$IMAGE_TAG" \
                          "./$SERVICE"

                        docker push "$ECR_URI:$SERVICE-$IMAGE_TAG"
                    done
                '''
            }
        }
    }

    post {
        always {
            sh 'docker logout "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com" || true'
        }

        success {
            echo 'All services built, tested, and pushed to ECR successfully.'
        }

        failure {
            echo 'Pipeline failed. Check the first failing stage in the console log.'
        }
    }
}
