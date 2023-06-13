FROM maven:3-jdk-11 AS build
WORKDIR /app

COPY . .

ARG BUILD_VER
ENV BUILD_VER=$BUILD_VER
ARG skiptest=false
ARG MAVEN_OPTS="-DskipTests=$skiptest -Dmaven.test.failure.ignore=true"
ENV MAVEN_OPTS=$MAVEN_OPTS
ARG MAVEN_PUBLISH_KEY
ARG SONAR_SCAN=enabled
ENV SONAR_SCAN=$SONAR_SCAN

RUN sed -i -r "s/MAVENPUBLISHKEY/${MAVEN_PUBLISH_KEY}/g" maven_settings.xml \
 && chmod 755 /app/set-deploy-version.sh \
 && bash ./set-deploy-version.sh \
 && mkdir -p /app/target/jacoco-aggregate \
 && chmod 755 /app/sonarscanner/sonarnet.sh \
 && echo "MAVEN_OPTS=$MAVEN_OPTS; SONAR_SCAN=$SONAR_SCAN" \
 && /app/sonarscanner/sonarnet.sh mvn -X -s maven_settings.xml clean package \
 && mvn -X -s maven_settings.xml deploy -fn -P nexus-deploy \
