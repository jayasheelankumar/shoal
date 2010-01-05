#!/bin/sh 

#
# Copyright 2004-2005 Sun Microsystems, Inc.  All rights reserved.
# Use is subject to license terms.
#
 #
 # The contents of this file are subject to the terms
 # of the Common Development and Distribution License
 # (the License).  You may not use this file except in
 # compliance with the License.
 #
 # You can obtain a copy of the license at
 # https://shoal.dev.java.net/public/CDDLv1.0.html
 #
 # See the License for the specific language governing
 # permissions and limitations under the License.
 #
 # When distributing Covered Code, include this CDDL
 # Header Notice in each file and include the License file
 # at
 # If applicable, add the following below the CDDL Header,
 # with the fields enclosed by brackets [] replaced by
 # you own identifying information:
 # "Portions Copyrighted [year] [name of copyright owner]"
 #
 # Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 #
#!/bin/sh

PWD=`pwd`
ECHO=`which echo`


#########################################
# Create the scripts used to run the test
#########################################

#===============================================
# Create the script that actually runs the test
#===============================================
rm -rf /tmp/script1
cat << ENDSCRIPT > /tmp/script1
#!/bin/sh +x

publish_home=$PWD/dist
lib_home=$PWD/lib

java -Dcom.sun.management.jmxremote -DLOG_LEVEL=\$5 -cp \${publish_home}/shoal-gms.jar:\${lib_home}/bcprov-jdk14.jar:\${lib_home}/grizzly-framework.jar:\${lib_home}/grizzly-utils.jar -DTCPSTARTPORT=\$6 -DTCPENDPORT=\$7 -DSHOAL_GROUP_COMMUNICATION_PROVIDER=grizzly com.sun.enterprise.shoal.messagesenderreceiver.SenderReceiver \$1 \$2 \$3 \$4

ENDSCRIPT
#===============================================

#=====================================================================
# Create the script monitors that monitors when  testing is complete
#=====================================================================
rm -rf /tmp/script2
cat << ENDSCRIPT > /tmp/script2
#!/bin/sh +x

ECHO=\`which echo\`
num=\$1

#num=\`ls -al instance*log | wc -l | sed -e 's/ //g' \`
count=\`grep "Testing Complete" instance*log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the (\$num) instances to complete testing"
\$ECHO -n "\$count"
while [ \$count -ne \$num ]
do
\$ECHO -n ",\$count"
count=\`grep "Testing Complete" instance*log | wc -l | sed -e 's/ //g' \`
sleep 5
done
\$ECHO ", \$count"

count=\`grep "Testing Complete" server.log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the DAS to complete testing"
\$ECHO -n "\$count"
while [ \$count -ne 1 ]
do
\$ECHO -n ",\$count"
count=\`grep "Testing Complete" server.log | wc -l | sed -e 's/ //g' \`
sleep 5
done

\$ECHO  ", \$count"
\$ECHO  "The following logs contain failures:"
\$ECHO  "==============="
grep "FAILED" instance*log
\$ECHO  "==============="
\$ECHO  "The following are the time results for sending messages:"
grep "Sending Messages Time data" instance*log
\$ECHO  "==============="
\$ECHO  "The following are the time results for receiving messages:"
grep "Receiving Messages Time data" instance*log
\$ECHO  "==============="
\$ECHO  "The following are exceptions found in the logs:"
\$ECHO  "==============="
grep "Exception" instance*log
grep "Exception" server.log
\$ECHO  "==============="
\$ECHO  "The following are SEVERE messages found in the logs:"
\$ECHO  "==============="
grep "SEVERE" instance*log
grep "SEVERE" server.log
\$ECHO  "==============="

exit 0

ENDSCRIPT
#=====================================================================

#=====================================================================
# Create the script monitors that monitors when  testing is complete
#=====================================================================
rm -rf /tmp/script3
cat << ENDSCRIPT > /tmp/script3
#!/bin/sh +x


ECHO=\`which echo\`
num=\$1
#num=\`ls -al instance*log | wc -l | sed -e 's/ //g' \`
count=\`grep "All members have joined the group" instance*log | wc -l | sed -e 's/ //g' \`
\$ECHO "Waiting for the (\$num) instances to join group"
\$ECHO -n "\$count"
while [ \$count -ne \$num ]
do
\$ECHO -n ",\$count"
count=\`grep "All members have joined the group" instance*log | wc -l | sed -e 's/ //g' \`
sleep 5
done
\$ECHO  ", \$count"
\$ECHO "All (\$num) instances have joined the group, testing will now begin"

exit 0

ENDSCRIPT
#=====================================================================

############################################
# This is where test execution really begins
############################################

usage () {
    cat << USAGE
Usage: $0
`/tmp/script1 -h`
The optional parameters are : <log level> <tcpstartport num> <tcpendport>

   <tcpstartport> and <tcpendport> are optional.  Grizzly and jxta transports have different defaults.
USAGE
exit 1
}


chmod 755 /tmp/script1
chmod 755 /tmp/script2
chmod 755 /tmp/script3
rm -rf instance*.log server.log

if [ "$1" == "-h" ]; then
usage
fi

# this value must match the number of /tmp/script1 calls below
numInstances="10"
#msgSize=1024
#numOfMsgs=7000
msgSize=100
numOfMsgs=100

$ECHO "Message size=${msgSize}"
$ECHO "Number of messages=${numOfMsgs}"

$ECHO "Starting DAS"
/tmp/script1 server ${numInstances} >& server.log &
# give time for the DAS to start
sleep 5
$ECHO "Starting ${numInstances} instances"

/tmp/script1 instance101 ${numInstances} ${msgSize} ${numOfMsgs} 9130 9160 >& instance101.log &
/tmp/script1 instance102 ${numInstances} ${msgSize} ${numOfMsgs} 9160 9190 >& instance102.log &
/tmp/script1 instance103 ${numInstances} ${msgSize} ${numOfMsgs} 9230 9260 >& instance103.log &
/tmp/script1 instance104 ${numInstances} ${msgSize} ${numOfMsgs} 9261 9290 >& instance104.log &
/tmp/script1 instance105 ${numInstances} ${msgSize} ${numOfMsgs} 9330 9360 >& instance105.log &
/tmp/script1 instance106 ${numInstances} ${msgSize} ${numOfMsgs} 9361 9390 >& instance106.log &
/tmp/script1 instance107 ${numInstances} ${msgSize} ${numOfMsgs} 9430 9460 >& instance107.log &
/tmp/script1 instance108 ${numInstances} ${msgSize} ${numOfMsgs} 9461 9490 >& instance108.log &
/tmp/script1 instance109 ${numInstances} ${msgSize} ${numOfMsgs} 9530 9560 >& instance109.log &
/tmp/script1 instance110 ${numInstances} ${msgSize} ${numOfMsgs} 9561 9590 >& instance110.log &

# give time for the instances to start
sleep 3
# monitor for the testing to begin
/tmp/script3 ${numInstances}
# monitor when the testing is complete
/tmp/script2 ${numInstances}
