#!/bin/bash

set -ex

KEY_FILE=/frontend/nginx/etc/nginx/ssl/star.circlehost.key
CERT_FILE=/frontend/nginx/etc/nginx/ssl/star.circlehost.crt
SERVER_CERT_FILE=/frontend/nginx/etc/nginx/ssl/star.circlehost.pem

if [[ "$1" = 'start' ]]
then
    # Generate Cert if none is present
    if [[ ! -s ${KEY_FILE} ]]
    then
        mkdir -p /frontend/nginx/etc/nginx/ssl
        openssl req -batch -new \
            -x509 -newkey rsa:2048 -sha256 -nodes -days 365 \
            -subj '/C=US/ST=California/L=San Francisco/O=CircleCI/CN=*.circlehost' \
            -keyout ${KEY_FILE} \
            -out ${CERT_FILE}
    fi

    if [[ ! -s ${SERVER_CERT_FILE} ]]
    then
        cat ${KEY_FILE} ${CERT_FILE} > ${SERVER_CERT_FILE}
    fi

    (cat <<EOF
global
    daemon
    maxconn 4096

listen http
  bind 0.0.0.0:13000
  bind 0.0.0.0:14443 ssl crt ${SERVER_CERT_FILE}
  mode tcp
  server master 127.0.0.1:3000
  timeout client 3600s
  timeout connect 3600s
  timeout server 3600s

EOF
) > /etc/haproxy/haproxy.cfg

    # Generate HAProxy
    haproxy -f /etc/haproxy/haproxy.cfg

    lein repl :headless &
    lein run
fi

exec "$@"
