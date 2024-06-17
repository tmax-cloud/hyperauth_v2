package com.tmax.hyperauth.authenticator;

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class Outer2ndFactorAuthenticator implements Authenticator {
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        log.info("user [ "+ context.getUser().getUsername() + " ] Outer 2nd Factor Authenticator Start");
        try{
            AuthenticatorConfigModel config = context.getAuthenticatorConfig();
            UserModel user = context.getUser();
            RealmModel realm = context.getRealm();
            String tabId = context.getAuthenticationSession().getTabId();
            log.info("tabID : " + tabId);
            long ttl = AuthenticatorUtil.getConfigLong(config, AuthenticatorConstants.CONF_PRP_WAIT_SEC, 5 * 60L); // 5 minutes in s
            Long expiringAt = new Date().getTime() + (ttl * 1000);

            log.info("Outer 2nd Factor Authentication expiring at " + ttl + " sec");
            log.info("Expired in milli : " + expiringAt);
            // Validate Authenticator
            validateAuthenticator( context );

            // tabID, ExpiringAt 1차 인증을 거친 유저가 2차 인증을 기다리고 있다는 표시를 해야한다. (User Credential)
            storeUserOuterAuthInfo(context, tabId, expiringAt); // s --> ms

            String additionalParam = AuthenticatorUtil.getAttributeValue(user, AuthenticatorUtil.getConfigString(config, AuthenticatorConstants.CONF_PRP_ADDI_PARAM));
            if(StringUtils.isBlank(additionalParam)) throw new Exception("User additional parameter is not configured.");

            String outerUrl = AuthenticatorUtil.getConfigString(config, AuthenticatorConstants.CONF_PRP_OUTER_URL);
            if(StringUtils.isBlank(outerUrl)) throw new Exception("Outer 2 factor URL is not configured");


            //send param to outer 2 factor page [userName, realmName, tabID, addi param, url, expiringAt Attribute]
            Response challenge = context.form()
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_USER_NAME, user.getUsername())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_REALM_NAME, realm.getName())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_TAB_ID, tabId)
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_ADDI_PARAM, additionalParam)
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_URL, outerUrl)
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_EXP_AT, expiringAt)
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_WAIT_TTL, Long.valueOf(ttl).intValue())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_STATUS, true)
                    .createForm("outer-2nd-factor-wait.ftl");
            context.challenge(challenge);
        } catch(Exception e){
            log.error("Error Occurred", e);
            Response challenge = context.form()
                    .setError(e.getMessage())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_ERROR_MESSAGE, e.getMessage())
                    .createForm("outer-2nd-factor-wait-error.ftl");
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge);
        }
    }


    @Override
    public void action(AuthenticationFlowContext context) {
        //Check if User Credential 2nd-factor.auth-status is "on"

        //FIXME: change from hyperAuth v1
        List<CredentialModel> statusCredentials = context.getUser().credentialManager().getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_STATUS).collect(Collectors.toList());
        String outer2ndFactorStatus = statusCredentials.get(0).getCredentialData();

        if (outer2ndFactorStatus.equalsIgnoreCase("on")){
            log.info("Outer 2nd Factor Authentication Passed, User [ " + context.getUser().getUsername() + " ]");
            context.success();
        } else {
            log.error("Outer 2nd Factor Authentication Not Passed Yet, Redirect to Waiting Page User [ " + context.getUser().getUsername() + " ]");

            AuthenticatorConfigModel config = context.getAuthenticatorConfig();
            UserModel user = context.getUser();
            RealmModel realm = context.getRealm();
            String tabId = context.getAuthenticationSession().getTabId();
            long ttl = AuthenticatorUtil.getConfigLong(config, AuthenticatorConstants.CONF_PRP_WAIT_SEC, 5 * 60L); // 5 minutes in s

            //FIXME: change from hyperAuth v1
            List<CredentialModel> expTimeStringCredentials = context.getUser().credentialManager().getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_WAIT_EXP_TIME).collect(Collectors.toList());
            String expTimeString = expTimeStringCredentials.get(0).getCredentialData();

            log.info("Outer 2nd Factor Authentication TTL time : " + Long.valueOf(ttl).intValue() + " second");
            Response challenge = context.form()
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_USER_NAME, user.getUsername())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_REALM_NAME, realm.getName())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_TAB_ID, tabId)
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_ADDI_PARAM,
                            AuthenticatorUtil.getAttributeValue(context.getUser(),
                                    AuthenticatorUtil.getConfigString(config, AuthenticatorConstants.CONF_PRP_ADDI_PARAM)))
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_URL,
                            AuthenticatorUtil.getConfigString(config,AuthenticatorConstants.CONF_PRP_OUTER_URL ))
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_EXP_AT, expTimeString)
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_WAIT_TTL, Long.valueOf(ttl).intValue())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_STATUS, false)
                    .createForm("outer-2nd-factor-wait.ftl");

            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
        }
    }

    private void storeUserOuterAuthInfo(AuthenticationFlowContext context, String tabId, Long expiringAt) {
        // For outer-2nd-factor Tab ID
        CredentialModel storedOutercredentials = new CredentialModel();
        storedOutercredentials.setId(UUID.randomUUID().toString());
        storedOutercredentials.setCreatedDate(new Date().getTime());
        storedOutercredentials.setType(AuthenticatorConstants.USR_CRED_OUTER_TAB_ID);
        storedOutercredentials.setCredentialData(tabId);

        //FIXME: change from hyperAuth v1
        List<CredentialModel> storedOuterTabIdList = context.getUser().credentialManager().getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_TAB_ID).collect(Collectors.toList());
        removeCredentials(context, storedOuterTabIdList);

        context.getUser().credentialManager().createStoredCredential(storedOutercredentials);


        // For outer-2nd-factor Exp-Time
        CredentialModel expTimecredentials = new CredentialModel();
        expTimecredentials.setId(UUID.randomUUID().toString());
        expTimecredentials.setCreatedDate(new Date().getTime());
        expTimecredentials.setType(AuthenticatorConstants.USR_CRED_OUTER_WAIT_EXP_TIME);
        expTimecredentials.setCredentialData(expiringAt.toString());

        // Delete Previous Credentials if Exists
        List<CredentialModel> expTimeCredentialList = context.getUser().credentialManager().
                getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_WAIT_EXP_TIME).collect(Collectors.toList());
        removeCredentials(context, expTimeCredentialList);

        // Create New Credentials
        context.getUser().credentialManager().createStoredCredential(expTimecredentials);

        // For outer-2nd-factor Auth Status
        CredentialModel outerStatusCredentials = new CredentialModel();
        outerStatusCredentials.setId(UUID.randomUUID().toString());
        outerStatusCredentials.setCreatedDate(new Date().getTime());
        outerStatusCredentials.setType(AuthenticatorConstants.USR_CRED_OUTER_STATUS);
        outerStatusCredentials.setCredentialData("off");

        // Delete Previous Credentials if Exists
        List<CredentialModel> outerStatusCredentialList = context.getUser().credentialManager().getStoredCredentialsByTypeStream(AuthenticatorConstants.USR_CRED_OUTER_STATUS).collect(Collectors.toList());
        removeCredentials(context, outerStatusCredentialList);

        // Create New Credentials
        context.getUser().credentialManager().createStoredCredential(outerStatusCredentials);
    }

    private void validateAuthenticator( AuthenticationFlowContext context) {
        AuthenticationFlowError errorType = null;
        try{
            if (StringUtils.isEmpty(AuthenticatorUtil.getConfigString(context.getAuthenticatorConfig(), AuthenticatorConstants.CONF_PRP_ADDI_PARAM))){
                log.error(AuthenticatorConstants.CONF_PRP_ADDI_PARAM + " is not set in Authenticator Config");
                errorType = AuthenticationFlowError.INTERNAL_ERROR;
                throw new Exception(AuthenticatorConstants.CONF_PRP_ADDI_PARAM + " is not set in Authenticator Config");
            } else if (StringUtils.isEmpty(AuthenticatorUtil.getAttributeValue(context.getUser(),
                    AuthenticatorUtil.getConfigString(context.getAuthenticatorConfig(), AuthenticatorConstants.CONF_PRP_ADDI_PARAM)))){
                log.error(AuthenticatorConstants.CONF_PRP_ADDI_PARAM + " is not set in user [ "+ context.getUser().getUsername() + " ] Attributes");
                errorType = AuthenticationFlowError.INVALID_USER;
                throw new Exception(AuthenticatorConstants.CONF_PRP_ADDI_PARAM + " is not set in user [ "+ context.getUser().getUsername() + " ] Attributes");
            }

            if (StringUtils.isEmpty(AuthenticatorUtil.getConfigString(context.getAuthenticatorConfig(), AuthenticatorConstants.CONF_PRP_OUTER_URL))){
                log.error(AuthenticatorConstants.CONF_PRP_OUTER_URL + " is not set in Authenticator Config");
                errorType = AuthenticationFlowError.INTERNAL_ERROR;
                throw new Exception(AuthenticatorConstants.CONF_PRP_OUTER_URL + " is not set in Authenticator Config");
            }

        }catch (Exception e){
            Response challenge = context.form()
                    .setError(e.getMessage())
                    .setAttribute(AuthenticatorConstants.ATTR_PRP_OUTER_ERROR_MESSAGE, e.getMessage())
                    .createForm("outer-2nd-factor-wait-error.ftl");
            context.failureChallenge(errorType, challenge);
        }

    }

    private void removeCredentials(AuthenticationFlowContext context, List< CredentialModel > storedCredentials) {
        if ( storedCredentials != null && storedCredentials.size() > 0) {
            List<String> storedCredentialIds = new ArrayList<>();
            for( CredentialModel storedCredential : storedCredentials) {
                storedCredentialIds.add(storedCredential.getId());
            }
            for ( String id : storedCredentialIds) {
                context.getUser().credentialManager().removeStoredCredentialById(id);
            }
        }
    }

    @Override
    public boolean requiresUser() { return true; }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) { return true; }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) { }

    @Override
    public void close() { }
}
