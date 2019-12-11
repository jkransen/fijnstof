FROM openjdk:14-jdk-alpine

MAINTAINER jeroen@kransen.nl

COPY target/scala-2.12/fijnstof_2.12-1.2.jar /

# RUN dpkg -i /fijnstof_1.2_all.deb 

# RUN java -jar fijnstof_2.12-1.2.jar
