
pipeline {
    agent any
triggers {
    githubPush()
}
    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        AWS_REGION = 'ap-southeast-2'
        AWS_ACCOUNT = '684905329064'
        ECR_REPO = 'javatechflow/aws-practices'
    }

    stages {
        stage('Checkout GitHub') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/devJavaProject96/spring-boot-microservices.git'

                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()

                    env.ECR_URI =
                        "${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"
                }

                echo "Building commit: ${env.IMAGE_TAG}"
            }
        }

        stage('Verify Tools and AWS Access') {
            steps {
                sh '''
                    set -eu
                    java -version
                    mvn -version
                    docker --version
                    aws --version

                    aws sts get-caller-identity
                '''
            }
        }

        stage('Build and Test All Services') {
            steps {
                sh '''
                    set -eu

                    SERVICES="
                    APIGateway
                    AdminServer
                    ConfigServer
                    NotificationServices
                    OrderServices
                    PaymentServices
                    ProductService
                    ServiceRegistry
                    ZipkinService
                    "

                    for SERVICE in $SERVICES; do
                        echo "========== Building $SERVICE =========="

                        test -f "$SERVICE/pom.xml" || {
                            echo "Missing $SERVICE/pom.xml"
                            exit 1
                        }

                        mvn -B -f "$SERVICE/pom.xml" clean verify
                    done
                '''
            }
        }

        stage('Login to Amazon ECR') {
            steps {
                sh '''
                    set -eu

                    aws ecr describe-repositories \
                        --region "$AWS_REGION" \
                        --repository-names "$ECR_REPO"

                    aws ecr get-login-password \
                        --region "$AWS_REGION" |
                    docker login \
                        --username AWS \
                        --password-stdin \
                        "$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com"
                '''
            }
        }

        stage('Build and Push All Docker Images') {
            steps {
                sh '''
                    set -eu

                    SERVICES="
                    APIGateway
                    AdminServer
                    ConfigServer
                    NotificationServices
                    OrderServices
                    PaymentServices
                    ProductService
                    ServiceRegistry
                    ZipkinService
                    "

                    for SERVICE in $SERVICES; do
                        echo "========== Packaging image: $SERVICE =========="

                        JAR_FILE=$(find "$SERVICE/target" \
                            -maxdepth 1 \
                            -type f \
                            -name '*.jar' \
                            ! -name '*-sources.jar' \
                            ! -name '*-javadoc.jar' \
                            ! -name 'original-*.jar' \
                            ! -name '*-plain.jar' \
                            -print -quit)

                        if [ -z "$JAR_FILE" ]; then
                            echo "No executable JAR found for $SERVICE"
                            exit 1
                        fi

                        cp "$JAR_FILE" "$SERVICE/app.jar"

                        cat > "$SERVICE/Dockerfile.jenkins" <<'DOCKERFILE'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
DOCKERFILE

                        IMAGE_TAG="$SERVICE-$IMAGE_TAG"
                        IMAGE="$ECR_URI:$IMAGE_TAG"

                        docker build \
                            -f "$SERVICE/Dockerfile.jenkins" \
                            -t "$IMAGE" \
                            "$SERVICE"

                        docker push "$IMAGE"

                        rm -f "$SERVICE/app.jar" \
                              "$SERVICE/Dockerfile.jenkins"

                        echo "Successfully pushed $IMAGE"
                    done
                '''
            }
        }
    }

    post {
        success {
            echo 'All service images were built and pushed to ECR.'
        }

        failure {
            echo 'Pipeline failed. Check the first failing stage in Console Output.'
        }

        always {
            sh 'docker logout "$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com" || true'
        }
    }
}

