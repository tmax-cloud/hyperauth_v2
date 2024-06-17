<#import "template.ftl" as layout>
<@layout.registrationLayout; section>
    <style>
        .centralElement {
            display : flex;
            justify-content : center;
            align-items : center;
        }
        #opt-code .timer-input-container::after {
            position: absolute;
            color: grey;
            content: attr(data-value);
        }
        .leftHorizontal{
            display : flex;
            justify-content : left;
        }
        .centralHorizontal{
            display : flex;
            justify-content : center;
        }
    </style>
    <#if properties.styles_template?has_content>
        <#list properties.styles_outer_2nd_factor_wait?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if section = "header">
        <div class="header-icon ip-blocked">
            ${msg("MSG_OUTER_2ND_FACTOR_1")}
        </div>
    <#elseif section = "form">
        <div id="opt-code">
            <body onload="startTimer('${(outerAuthExpAt!'')}', '${(outerAuthWaitTtl!'')}')">
                <form id="otp-code-form" action="${url.loginAction}" method="post">
                    <div class="${properties.kcFormGroupClass!} centralElement">
                        <p id="instruction">
                            <#if outerAuthStatus>
                                ${msg("MSG_OUTER_2ND_FACTOR_2")?no_esc}
                            <#else>
                                ${msg("MSG_OUTER_2ND_FACTOR_8")}
                            </#if>
                        </p>
                    </div>
                    <div class="${properties.kcFormGroupClass!} marginTop">
                        <div class="${properties.kcLabelWrapperClass!} centralElement">
                            <a id="outer2ndFactorLink" class="authLinkButton" href="${outerUrl}?user_name=${userName}&realm_name=${realmName}&tab_id=${tabId}&additional_param=${additionalParameter}" target = '_blank'  onclick="enableConfirmButton();">${msg("MSG_OUTER_2ND_FACTOR_3")}</a>
                        </div>
                        <br><br>
                        <div id="timer-input-container" class="timer-input-container centralElement" data-value="0:00"></div>
                    </div>
                    <div class="${properties.kcFormGroupClass!}">
                        <div id="kc-form-buttons" class="${properties.kcFormButtonsClass!} centralElement">
                            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!} ${properties.kcButtonLargeClass!}" id="confirm-button" type="submit" value ="${msg("OUTER_2ND_FACTOR_CONFIRM")}" disabled/>
                        </div>
                        <div id="kc-info-message" class="${properties.kcLabelWrapperClass!} infoText">
                            <br>
                            <div class="leftHorizontal">
                                <div style="width:40%;">${msg("MSG_OUTER_2ND_FACTOR_4")}</div>
                                <a id="resend-link" href="#" onclick="clickResendButton(document.location.href); return false;">${msg("MSG_OUTER_2ND_FACTOR_5")}</a></p>
                            </div>
                            <#if client?? && url.loginRestartFlowUrl?has_content>
                            <div class="leftHorizontal">
                                <div style="width:40%;">${msg("MSG_OUTER_2ND_FACTOR_6")}</div>
                                <a href="${url.loginRestartFlowUrl}" style="color: #185692;">${msg("MSG_OUTER_2ND_FACTOR_7")}</a></p>
                            </div>
                            </#if>
                            <br>
                        </div>
                        <div class="modal hidden">
                            <div class="md_overlay"></div>
                            <div class="md_content">
                                <div class="md_content__header">
                                    <span class="md_content__header__title">
                                        ${msg("MSG_OUTER_2ND_FACTOR_POPUP_1")}
                                    </span>
                                    <span class="md_content__header__close" onclick="closeModal()">
                                    </span>
                                </div>
                                <hr>
                                <div class="md_content__text">
                                    ${msg("MSG_OUTER_2ND_FACTOR_POPUP_2")}
                                </div>
                                <div class="button" onclick="closeModal()">
                                    ${msg("MSG_OUTER_2ND_FACTOR_POPUP_3")}
                                </div>
                            </div>
                        </div>
                        <div class="time_expired_modal hidden">
                            <div class="md_overlay"></div>
                            <div class="md_content">
                                <div class="md_content__header">
                                    <span class="md_content__header__title" style="text-align:center;">
                                        <p>${msg("tempOtpCodeTimeExpiredTitle")}</p>
                                    </span>
                                    <span class="md_content__header__close" onclick="closeTimeExpiredModal()">
                                    </span>
                                </div>
                                <div class="md_content__text centralElement">
                                    ${msg("outer2ndFactorTimeExpiredMessage")?no_esc}
                                </div>
                            </div>
                        </div>
                    </div>
                </form>
            </body>
        </div>
    </#if>
    <script type="text/javascript" src="${url.resourcesPath}/js/axios.min.js"></script>
    <script type="text/javascript" src="${url.resourcesPath}/js/outer-2nd-factor-wait.js?${properties.version}"></script>
</@layout.registrationLayout>