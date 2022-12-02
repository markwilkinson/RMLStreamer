FROM eclipse-temurin:19-jre
RUN mkdir /opt/app
ENV LANG="en_US.UTF-8" LANGUAGE="en_US:UTF-8" LC_ALL="C.UTF-8"
RUN chmod a+r /etc/resolv.conf


RUN apt-get dist-upgrade -q && \
    apt-get update -q
RUN apt-get install -y --no-install-recommends build-essential lighttpd 

COPY Server/lighttpd.conf /etc/lighttpd/
COPY Server/10-cgi.conf /etc/lighttpd/conf-enabled/
RUN service lighttpd restart

COPY target/RMLStreamer-*.jar /opt/app/RMLStreamer.jar
#ENTRYPOINT ["java", "-jar", "/opt/app/RMLStreamer.jar"]
ENTRYPOINT ["perl", "-v"]
