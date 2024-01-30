from quay.io/keycloak/keycloak:18.0.1
 
LABEL maintainer="seongmin_lee2@tmax.co.kr"

# 1. change keycloak.conf
RUN rm /opt/keycloak/conf/keycloak.conf
ADD script/keycloak.conf /opt/keycloak/conf/keycloak.conf

# 2. add theme
ADD themes/* /opt/jboss/keycloak/themes/*

# 3. add hyperauth-spi.jar (SPI)
ADD target/hyperauth-spi-18.0.1.jar /opt/keycloak/providers/hyperauth-spi-18.0.1.jar

# 4. hyperauth-spi.jar (SPI)
ADD target/keycloak-spi-jar-with-dependencies.jar /opt/jboss/keycloak/standalone/deployments/hyperauth-spi.jar

# 5. idp spi html
ADD src/main/resources/theme-resources/base/admin/resources/partials/* /opt/keycloak/themes/base/admin/resources/partials/

