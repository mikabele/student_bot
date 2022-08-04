FROM ubuntu:20.04

#tar timezone configs
ENV TIMEZONE=Europe/Minsk
RUN ln -snf /usr/share/zoneinfo/$TIMEZONE /etc/localtime && \
    echo $TIMEZONE > /etc/timezone

#install main utilities
RUN apt-get update && \
    apt-get install -q -y curl && \
    apt-get install -q -y nano && \
    apt-get install -q -y unzip && \
    apt-get install -q -y zip && \
    apt-get install -q -y sed && \
    apt-get install -q -y git-all

#install java
RUN apt-get install -q -y openjdk-17-jdk

ENV SBT_VERSION 1.6.2

#install sbt
RUN curl -L -o sbt-$SBT_VERSION.zip https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.zip && \
    unzip sbt-$SBT_VERSION.zip -d ops

ENV PATH="/ops/sbt/bin:$PATH"

#install postgres client
RUN apt-get install -q -y postgresql-client

