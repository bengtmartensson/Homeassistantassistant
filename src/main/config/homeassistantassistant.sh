#!/bin/sh

JAVA=java
JAR=target/HomeAssistantAssistant-*-jar-with-dependencies.jar

exec ${JAVA} -jar ${JAR}  "$@"
