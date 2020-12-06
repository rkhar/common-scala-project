#
# Scala and sbt Dockerfile
#
# https://github.com/hseeberger/scala-sbt
#

# Pull base image OpenJDK
FROM 336654285663.dkr.ecr.eu-west-1.amazonaws.com/cicd/job-images/alpine-jdk-sbt:v2.12

MAINTAINER Rassul Khar <rassul.khar@gmail.com>

#ENV APP_ID some-app-id
ENV APP_NAME app.zip
ENV APP_DIR app
ENV RUN_SCRIPT start-server
# ENV LOG_DIR /dar/logs/$APP_ID
# ENV LOG_ARCHIVE_DIR /dar/logs/archive/$APP_ID

# RUN mkdir -p $LOG_DIR \
#     && mkdir -p $LOG_ARCHIVE_DIR

WORKDIR /root
COPY ./target/universal/$APP_NAME /root/
COPY ./src/main/resources/data/*.pdf /root/documents/
RUN unzip -q $APP_NAME
WORKDIR /root/$APP_DIR/bin

# clean zip
RUN rm /root/$APP_NAME

# Clean bat file in order to leave a single starter file.
# Later it will be used to start the script. (sorry, this is done in lack of experience in bash :D )
RUN rm ./*.bat

# Here it is, a dummy but a simple way to solve the issue (may be it's better to use something like `ls -t * | head -1`)
RUN cp ./* ./$RUN_SCRIPT
CMD chmod +x $RUN_SCRIPT

CMD ["/bin/bash", "-c", "./$RUN_SCRIPT -Dlogback.configurationFile=${LOGBACK}"]
