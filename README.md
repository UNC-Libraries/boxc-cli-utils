# Box-c CLI Utils
#### To build:
`mvn clean install`

#### Usage:
Direct invocation:
`java -jar target/boxc-cli-utils.jar`

#### To debug the built jar:
`java -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y -jar target/boxc-cli-utils.jar`
  
## Requeuing failed services
To move failed enhancement messages from the DLQ back to the enhancement queue:
`sudo dcr_utils.sh dlq requeue_enhancements`

### Requeuing longleaf messages

```bash
# Tunnel to activemq
ssh <server-name> -L 61616:<server-name>:61616
# Do a dry run of requeuing failed registration jobs
java -jar target/box-cli-utils.jar dlq -n requeue_longleaf_register
# Requeue 5 failed registration jobs
java -jar target/box-cli-utils.jar dlq -l 5 requeue_longleaf_register
```