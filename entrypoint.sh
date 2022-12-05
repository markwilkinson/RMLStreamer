#! /bin/sh
#
# entrypoint.sh

/usr/sbin/lighttpd -D -f /etc/lighttpd/lighttpd.conf
