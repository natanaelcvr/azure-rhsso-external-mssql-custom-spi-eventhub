FROM registry.access.redhat.com/ubi8/openjdk-11:1.12-1.1651233100 AS builder

ARG MAVEN_MIRROR_URL
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}

WORKDIR /opt/java/app/
COPY . ./
USER root
RUN chmod -R 777 /opt/java/app

USER jboss
# RUN sed -i -e 's/<mirrors>/&\n    <mirror>\n      <id>external<\/id>\n      <url>${env.MAVEN_MIRROR_URL}<\/url>\n      <mirrorOf>external:*<\/mirrorOf>\n    <\/mirror>/' ${HOME}/.m2/settings.xml && \
RUN mvn -s settings.xml clean package

FROM registry.redhat.io/rh-sso-7/sso76-openshift-rhel8:7.6

COPY --from=builder /opt/java/app/target/*.jar ${JBOSS_HOME}/standalone/deployments/.
COPY mssql-jdbc-9.2.0.jre8.jar ${JBOSS_HOME}/extensions/jdbc-driver.jar
COPY sso-extensions.cli ${JBOSS_HOME}/extensions/.

USER 185
CMD ["/opt/eap/bin/openshift-launch.sh"]
