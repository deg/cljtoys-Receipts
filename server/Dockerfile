FROM java:8-alpine
MAINTAINER David Goldfarb <deg@degel.com>

ADD target/receipts-server-0.0.1-SNAPSHOT-standalone.jar /receipts-server/app.jar

EXPOSE 8080

CMD ["java", "-Xmx500m", "-jar", "/receipts-server/app.jar"]
