from quay.io/keycloak/keycloak:23.0.6

LABEL maintainer="seongmin_lee2@tmax.co.kr"

# 1. change keycloak.conf
ADD script/keycloak.conf /opt/keycloak/conf/keycloak.conf

# 2. add theme
#ADD themes/base /opt/keycloak/themes/base
#ADD themes/keycloak /opt/keycloak/themes/keycloak
ADD themes/shinhan-life /opt/keycloak/themes/shinhan-life

# 3. add hyperauth-spi.jar (SPI)
ADD target/hyperauth-2.0.0.jar /opt/keycloak/providers/hyperauth-2.0.0.jar

# 4. add custom core jar
#RUN chmod -R 777 /opt/keycloak/lib/lib/main/org.keycloak.keycloak-model-jpa-23.0.6.jar
#RUN rm /opt/keycloak/lib/lib/main/org.keycloak.keycloak-model-jpa-23.0.6.jar
ADD libs/keycloak-model-jpa-23.0.6.jar /opt/keycloak/lib/lib/main/org.keycloak.keycloak-model-jpa-23.0.6.jar

# 5. for mTLS (SSL)
RUN mkdir /opt/keycloak/ssl

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]


