FROM maven:3-jdk-8 as build
WORKDIR /usr/src/app

COPY . .

ARG MAVEN_PUBLISH_KEY
RUN sed -i -r "s/MAVENPUBLISHKEY/${MAVEN_PUBLISH_KEY}/g" maven_settings.xml

ARG BUILD_VER
ENV BUILD_VER=$BUILD_VER
RUN bash ./set-deploy-version.sh

RUN mvn -X -s maven_settings.xml clean package
RUN mvn -X -s maven_settings.xml deploy -fn -P nexus-deploy
