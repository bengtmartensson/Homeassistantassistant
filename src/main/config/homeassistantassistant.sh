#!/bin/sh

JAVA=java
HOME="$(dirname -- "$(readlink -f -- "${0}")" )"
JAR=${HOME}/HomeAssistantAssistant-*-jar-with-dependencies.jar
HA_HOST=homeassistant
TOKEN=@${HOME}/token.txt

exec ${JAVA} -jar ${JAR} --host ${HA_HOST} --token ${TOKEN}  "$@"
