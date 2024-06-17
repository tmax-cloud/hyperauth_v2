package com.tmax.hyperauth.rest;


import com.tmax.hyperauth.authenticator.AuthenticatorConstants;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import liquibase.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.spi.HttpResponse;
import org.keycloak.common.ClientConnection;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.resource.RealmResourceProvider;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class Outer2ndFactorProvider implements RealmResourceProvider {
    @Context
    private KeycloakSession session;

    @Context
    private HttpResponse response;

    @Context
    private ClientConnection clientConnection;

    public Outer2ndFactorProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    Response.Status status = null;
    String out = null;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@QueryParam("user_name") String userName, @QueryParam("realm_name") String realm_name, @QueryParam("tab_id") String tabId,
                        @QueryParam("additional_param") String addiParam, @QueryParam("secret_key") String secretKey) {
        log.info("***** GET /outer2ndFactor");

        //Check If User with the TabID is Waiting
        log.info("received auth request : [user_name: " + userName + ", realm_name: " + realm_name + ", tab_id: " + tabId + ", additional_param: " + addiParam + "]");

        try {

            StringBuilder missingParam = new StringBuilder();

            if(StringUtil.isEmpty(userName)) missingParam.append("userName ");
            if(StringUtil.isEmpty(realm_name)) missingParam.append("realm_name ");
            if(StringUtil.isEmpty(tabId)) missingParam.append("tabId ");
            if(StringUtil.isEmpty(addiParam)) missingParam.append("additionalParam ");
            if(StringUtil.isEmpty(secretKey)) missingParam.append("secretKey ");

            if (missingParam.length() > 0) {
                log.error("Parameter missing");
                status = Response.Status.BAD_REQUEST;
                out = "Outer 2nd Factor Authenticate Failed, Parameter missing : [" + missingParam + "]";
                return setCors(status, out);
            }

            RealmModel realm = session.realms().getRealmByName(realm_name);
            UserModel user = session.users().getUserByUsername(realm,userName);

            List<CredentialModel> expTimeCredentialList = session.users().getUserByUsername(realm, userName).credentialManager()
                    .getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_WAIT_EXP_TIME).collect(Collectors.toList());
            for (int i = 0; i < expTimeCredentialList.size(); i++) {
                log.info("expTimeCredentialList " + i + ": "+ expTimeCredentialList.get(i).getCredentialData());
            }
            String expTimeString = expTimeCredentialList.get(0).getCredentialData();
            Long currentTime = new Date().getTime();
            if (Long.parseLong(expTimeString) < currentTime) {
                log.error("Outer 2nd Factor Authenticator Expired [exp: {}, current: {}]", expTimeString, currentTime);
                status = Response.Status.BAD_REQUEST;
                out = "Outer 2nd Factor Authenticate Failed, Expired";
                return setCors(status, out);
            }

            if (!realm.getAuthenticatorConfigByAlias(AuthenticatorConstants.CONF_PRP_OUTER_2ND_FACTOR_ALIAS)
                    .getConfig().get(AuthenticatorConstants.CONF_PRP_SECRET_KEY).equalsIgnoreCase(secretKey)){
                log.error("Secret Key Not Matched");
                status = Response.Status.UNAUTHORIZED;
                out = "Outer 2nd Factor Authenticate Failed, Secret Key Not Matched";
                return setCors(status, out);
            } else {
                log.info("Secret Key Matched");
            }

            // TabID Check
            List<CredentialModel> tabIdCredentialList = session.users().getUserByUsername(realm, userName).credentialManager()
                    .getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_TAB_ID).collect(Collectors.toList());
            if ( tabIdCredentialList != null
                    && tabIdCredentialList.get(0).getCredentialData().equalsIgnoreCase(tabId)){
                log.info("Tab ID Matched");

                // Additional Param Check
                String addiParamKey = realm.getAuthenticatorConfigByAlias(AuthenticatorConstants.CONF_PRP_OUTER_2ND_FACTOR_ALIAS)
                        .getConfig().get(AuthenticatorConstants.CONF_PRP_ADDI_PARAM);
                if ( user.getAttributes().get(addiParamKey).get(0).equalsIgnoreCase(addiParam)){
                    log.info("Additional Parameter Matched");
                    CredentialModel credentials = new CredentialModel();
                    credentials.setId(UUID.randomUUID().toString());
                    credentials.setCreatedDate(new Date().getTime());
                    credentials.setType(AuthenticatorConstants.USR_CRED_OUTER_STATUS);
                    credentials.setCredentialData("on");

                    // Delete Previous Credentials if Exists
                    List< CredentialModel > storedCredentials = session.users().getUserByUsername(realm, userName).credentialManager()
                            .getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_STATUS).collect(Collectors.toList());
                    removeCredentials(user, storedCredentials);

                    // Create New Credentials
                    session.users().getUserByUsername(realm,userName).credentialManager().createStoredCredential(credentials);

                    log.info(addiParamKey + " matched");
                    out = "Outer 2nd Factor Authenticate Status Update Success, User [ " + userName + " ]";
                    status = Response.Status.OK;
                    return setCors(status, out);

                } else{
                    log.error(addiParamKey + " not matched");
                    status = Response.Status.BAD_REQUEST;
                    out = "Outer 2nd Factor Authenticate Failed," +  addiParamKey + " not matched";
                    return setCors(status, out);
                }
            } else{
                log.error("tabId not matched");
                status = Response.Status.BAD_REQUEST;
                out = "Outer 2nd Factor Authenticate Failed, TabID not matched";
                return setCors(status, out);
            }

        }catch (Exception e) {
            log.error("Error Occurs!!", e);
            status = Response.Status.BAD_REQUEST;
            out = "Outer 2nd Factor Authenticate Failed";
            return setCors(status, out);
        }
    }


    @OPTIONS
    @Path("{path : .*}")
    public Response other() {
        log.info("***** OPTIONS /outer2ndFactor");
        return setCors( Response.Status.OK, null);
    }

    @Override
    public void close() {
    }

    private void removeCredentials(UserModel user, List< CredentialModel > storedCredentials) {
        if ( storedCredentials != null && storedCredentials.size() > 0) {
            List<String> storedCredentialIds = new ArrayList<>();
            for( CredentialModel storedCredential : storedCredentials) {
                storedCredentialIds.add(storedCredential.getId());
            }
            for ( String id : storedCredentialIds) {
                user.credentialManager().removeStoredCredentialById(id);
//                session.userCredentialManager().removeStoredCredential(session.getContext().getRealm(), user , storedCredential.getId());
            }
        }
    }

    public static Response setCors(Response.Status status, Object out ) {
        return Response.status(status).entity(out)
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Max-Age", "3628800")
                .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE, HEAD, PATCH")
                .header("Access-Control-Allow-Headers", "*" ).build();
    }
}