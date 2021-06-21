# Box-c CLI Utils
#### To build:
`mvn clean install`

#### Usage:
Using the wrapper script:
`sudo dcr_utils.sh`
or direct invocation:
`java -jar target/boxc-cli-utils.jar`

#### To debug the built jar:
`java -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y -jar target/boxc-cli-utils.jar`
  
## Requeuing failed services
To move failed enhancement messages from the DLQ back to the enhancement queue:
`sudo dcr_utils.sh dlq requeue_enhancements`