#!/bin/sh

JAVA=java
HOME="$(dirname -- "$(readlink -f -- "${0}")" )"
JAR=${HOME}/HomeAssistantAssistant-*-jar-with-dependencies.jar

exec ${JAVA} -jar ${JAR}  "$@"
