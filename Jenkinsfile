
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
                        "${env.AWS_ACCOUNT}.dkr.ecr.${env.AWS_REGION}.amazonaws.com/${env.ECR_REPO}"
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

            # Locate the Java installation used by Jenkins
            JAVA_HOME_REAL="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
            export JAVA_HOME="$JAVA_HOME_REAL"

            TRUST_DIR="$WORKSPACE/.rds-certs"
            TRUSTSTORE="$TRUST_DIR/cacerts"

            mkdir -p "$TRUST_DIR"

            # Preserve Java's existing trusted certificates
            cp "$JAVA_HOME/lib/security/cacerts" "$TRUSTSTORE"

            # Download the official AWS RDS CA bundle
            curl -fsSL \
              https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
              -o "$TRUST_DIR/global-bundle.pem"

            # Split the bundle into individual certificates
            awk -v dir="$TRUST_DIR" '
              /-----BEGIN CERTIFICATE-----/ {
                  n++
                  file = sprintf("%s/rds-ca-%02d.pem", dir, n)
              }
              file != "" { print > file }
              /-----END CERTIFICATE-----/ {
                  close(file)
                  file = ""
              }
            ' "$TRUST_DIR/global-bundle.pem"

            # Import certificates into the copied Java truststore
            for CERT in "$TRUST_DIR"/rds-ca-*.pem; do
                [ -f "$CERT" ] || continue

                ALIAS=$(basename "$CERT" .pem)

                keytool -importcert \
                    -noprompt \
                    -trustcacerts \
                    -alias "$ALIAS" \
                    -file "$CERT" \
                    -keystore "$TRUSTSTORE" \
                    -storepass changeit
            done

            # Apply trust settings to Maven and Java test processes
            export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=changeit"

            echo "Java RDS truststore configured."
            java -version
            mvn -version

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

                if [ ! -f "$SERVICE/pom.xml" ]; then
                    echo "Missing $SERVICE/pom.xml"
                    exit 1
                fi

                if ! mvn -B -e -f "$SERVICE/pom.xml" clean verify; then
                    echo "FAILED SERVICE: $SERVICE"
                    exit 1
                fi

                echo "SUCCESS: $SERVICE"
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
                        echo ""
                        echo "=========================================="
                        echo "Packaging Docker image: $SERVICE"
                        echo "=========================================="

                        test -d "$SERVICE/target" || {
                            echo "Missing target directory for $SERVICE"
                            exit 1
                        }

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
                            echo "ERROR: No executable JAR found for $SERVICE"
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

                        SERVICE_IMAGE_TAG="${SERVICE}-${IMAGE_TAG}"
                        IMAGE="${ECR_URI}:${SERVICE_IMAGE_TAG}"

                        docker build \
                            --pull \
                            -f "$SERVICE/Dockerfile.jenkins" \
                            -t "$IMAGE" \
                            "$SERVICE"

                        docker push "$IMAGE"

                        rm -f "$SERVICE/app.jar" \
                              "$SERVICE/Dockerfile.jenkins"

                        echo "Successfully pushed: $IMAGE"
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
            echo 'Pipeline failed. Check the first failing stage and service in Console Output.'
        }

        always {
            sh '''
                docker logout \
                    "$AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com" \
                    >/dev/null 2>&1 || true
            '''
        }
    }
}
