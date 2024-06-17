<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <#if section = "header">
         <div class="header-icon otp-send-failed">
            ${msg("otpCodeSendFailedTitle")}
        </div>
    <#elseif section = "form">
        <div id="otp-send-failed">
            <div class="${properties.kcFormGroupClass!}">
                <p id="instruction" >
                    <br>
                    <br>
                ${msg("outer2ndfactorFailedMessage1")}
                </p>
            </div>
            <div class="${properties.kcFormGroupClass!}">
                <button class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" style="margin-top: 95px; font-size: 18px;" <#if url.loginRestartFlowUrl?? && url.loginRestartFlowUrl!="">onclick="location.href='${url.loginRestartFlowUrl}'"<#else>onclick="location.href=document.location.origin"</#if>>${msg("MSG_OUTER_2ND_FACTOR_7")}</button>
            </div>
        </div>
        <#if properties.scripts_security_policy_hyperauth?has_content>
            <#list properties.scripts_security_policy_hyperauth?split(' ') as script>
                <script src="${url.resourcesPath}/${script}?${properties.version}" type="text/javascript"></script>
            </#list>
        </#if>
    </#if>
</@layout.registrationLayout>