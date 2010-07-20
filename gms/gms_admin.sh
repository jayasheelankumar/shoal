#!/bin/sh +x 

#
# Copyright 2010 Sun Microsystems, Inc.  All rights reserved.
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

DIST=false

TCPSTARTPORT=9060
TCPENDPORT=9089
MULTICASTADDRESS=229.9.1.2
MULTICASTPORT=2299

TEST_LOG_LEVEL=WARNING
SHOALGMS_LOG_LEVEL=WARNING
GMS_WORKSPACE_HOME=`pwd`

TRANSPORT=grizzly
BINDINGINTERFACEADDRESS=""

MAINCLASS=com.sun.enterprise.ee.cms.tests.GMSAdminCLI

if [ -f ./currentMulticastAddress.txt ]; then
    MULTICASTADDRESS=`cat currentMulticastAddress.txt`
    if [ -z "${MULTICASTADDRESS}" ]; then
        MULTICASTADDRESS=229.9.1.2
    fi
fi

usage() {
    echo "Usage:"
    echo "    list groupName [memberName(all is default)] - list member(s)"
    echo "    stopc groupName - stops a cluster"
    echo "    stopm groupName memberName - stop a member"
    echo "    killm groupName memberName - kill a member"
    echo "    killa groupName - kills all members of the cluster "
    echo "    waits groupName - wait for the cluster to complete startup"
    echo "    test groupName - start testing and wait until its complete"
    echo "    state groupName gmsmemberstate  - list member(s) in the specific state"
    echo
    echo " optional arguments:"
    echo "      [-d] [<-resend> [-t grizzly|jxta] <-tl level> <-sl level> <-ts TCPSTARTPORT> <-te TCPENDPORT> <-ma multicastaddress> <-mp multicastport>]"
    echo "      -d - distributed testing"
    echo "      -resend - if reply is not received, try 2 additional times"
    echo "      -t  - transport type (defaults to grizzly"
    echo "      -tl - test log level"
    echo "      -sl - shoal log level"
    echo "      -ts - tcp start port"
    echo "      -te - tcp end point"
    echo "      -ma - multicast address"
    echo "      -mp multicast port"
    exit 0
}

programhelp() {
    java -cp ${JARS} ${MAINCLASS} -h
    exit 0
}

if [ $# -lt 2 ]; then
     echo "Error: Command and GroupName must be specified"
     usage
fi

_OPTARGS=""
DONEREQUIRED=false
while [ $# -ne 0 ]
do
     case $1 in
       -h)
       usage
       ;;
       -d)
         shift
         DIST=true
       ;;
       -ph)
       programhelp
       ;;
       -swh)
       shift
       GMS_WORKSPACE_HOME="${1}"
       shift
       ;;
       -bia)
       shift
       BINDINGINTERFACEADDRESS="${1}"
       shift
       ;;
       -resend)
       _OPTARGS="${_OPTARGS}${1}"
       shift
       ;;
       -ts)
       shift
       TCPSTARTPORT="${1}"
       shift
       ;;
       -te)
       shift
       TCPENDPORT="${1}"
       shift
       ;;
       -ma)
       shift
       MULTICASTADDRESS="${1}"
       shift
       ;;
       -mp)
       shift
       MULTICASTPORT="${1}"
       shift
       ;;
       -tl)
       shift
       TEST_LOG_LEVEL="${1}"
       shift
       ;;
       -sl)
       shift
       SHOALGMS_LOG_LEVEL="${1}"
       shift
       ;;
       -t)
         shift
         TRANSPORT=${1}
         shift
         if [ ! -z "${TRANSPORT}" ] ;then
            if [ "${TRANSPORT}" != "grizzly" -a "${TRANSPORT}" != "jxta" ]; then
               echo "ERROR: Invalid transport specified"
               usage
            fi
         else
            echo "ERROR: Missing transport value"
            usage
         fi
       ;;
       *)
       if [ $DONEREQUIRED = false ]; then
           COMMAND="${1}"
           shift
           GROUPNAME="${1}"
           shift
           DONEREQUIRED=true
       else
           MEMBERNAME="${1}"
           shift
       fi
       ;;
     esac
done


PUBLISH_HOME=${GMS_WORKSPACE_HOME}/dist
LIB_HOME=${GMS_WORKSPACE_HOME}/lib
GRIZZLY_JARS=${PUBLISH_HOME}/shoal-gms-tests.jar:${PUBLISH_HOME}/shoal-gms.jar:${LIB_HOME}/grizzly-framework.jar:${LIB_HOME}/grizzly-utils.jar
JXTA_JARS=${LIB_HOME}/jxta.jar:${GRIZZLY_JARS}
JARS=${GRIZZLY_JARS}

OPTARGS=""
if [ ! -z "${_OPTARGS}" ];then
   OPTARGS="-DOPTARGS=${_OPTARGS}"
fi
if [ $TRANSPORT != "grizzly" ]; then
    JARS=${JXTA_JARS}
fi
if [ ! -z ${BINDINGINTERFACEADDRESS} ]; then
    BINDINGINTERFACEADDRESS="-DBIND_INTERFACE_ADDRESS=${BINDINGINTERFACEADDRESS}"
fi
# echo java -Dcom.sun.management.jmxremote -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} -DTCPSTARTPORT=${TCPSTARTPORT} -DTCPENDPORT=${TCPENDPORT} -DMULTICASTADDRESS=${MULTICASTADDRESS} -DMULTICASTPORT=${MULTICASTPORT} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DLOG_LEVEL=${SHOALGMS_LOG_LEVEL} ${OPTARGS} -cp ${JARS} $MAINCLASS ${COMMAND} ${GROUPNAME} ${MEMBERNAME}
java -Dcom.sun.management.jmxremote -DSHOAL_GROUP_COMMUNICATION_PROVIDER=${TRANSPORT} -DTCPSTARTPORT=${TCPSTARTPORT} -DTCPENDPORT=${TCPENDPORT} -DMULTICASTADDRESS=${MULTICASTADDRESS} -DMULTICASTPORT=${MULTICASTPORT} ${BINDINGINTERFACEADDRESS} -DTEST_LOG_LEVEL=${TEST_LOG_LEVEL} -DLOG_LEVEL=${SHOALGMS_LOG_LEVEL} ${OPTARGS} -cp ${JARS} $MAINCLASS ${COMMAND} ${GROUPNAME} ${MEMBERNAME}





