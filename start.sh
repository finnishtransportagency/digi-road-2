#!/bin/sh
javamemory="-Xms2048m -Xmx12288m"
jarfile="target/scala-2.11/digiroad2-assembly-0.1.0-SNAPSHOT.jar"
javaopts="$javamemory -Dfile.encoding=UTF8 -Djava.security.egd=file:///dev/urandom -jar $jarfile"
logfile="digiroad2.boot.log"
jmxmonitoring="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.rmi.port=9010"

nohup java $jmxmonitoring $javaopts > $logfile &
