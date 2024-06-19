# HyperAuth v2 설치 가이드
## 개요
* Hyperauth v2
  * Based on Keycloak 23.0.6 (2024.02.06 기준)
## 구성 요소 및 버전
* hyperauth
    * [hyperregistry.tmaxclouod.org/hyperauth/hyperauth:b2.x.x](https://hyperregistry.tmaxcloud.org/harbor/projects/20/repositories/hyperauth)
* postgres
    * postgres:14-alpine


## 설치 가이드  

### Step 1. 초기화 작업 및 yaml 수정
* 목적 : `HyperAuth 구축을 위한 초기화 작업, Secret생성 및 DB 구축, Yaml 버전 수정`
* 생성 순서 :
   * [1.initialization.yaml](manifest/1.initialization.yaml) 실행 `ex) kubectl apply -f 1.initialization.yaml`)
* 비고 : 아래 명령어 수행 후, Postgre Admin 접속 확인
```bash
    $ kubectl exec -it $(kubectl get pods -n hyperauth | grep postgre | cut -d ' ' -f1) -n hyperauth -- bash
    $ psql -U keycloak keycloak
 ```

### Step 2. SSL 인증서 생성
* 목적 : `HTTPS 인증을 위한 인증서, kafka와의 통신을 위한 keystore, truststore를 생성하고 secret으로 변환`
* 생성 순서 :
  * cert-manager가 설치되어 있고, tmaxcloud-issuer (ClusterIssuer) 가 생성되어 있다고 가정한다.
    * cert-manager 설치는 https://cert-manager.io/docs/installation
    * 생성이 안되어 있는 경우, [tmaxcloud-issuer.yaml](manifest/tmaxcloud-issuer.yaml) 실행 `ex) kubectl apply -f tmaxcloud-issuer.yaml`)
  * [hyperauth_certs.yaml](manifest/hyperauth_certs.yaml) 의 변수를 상황에 맞게 치환한다. 안쓰는 변수 부분은 지워준다.
    * Hyperauth
      * Hyperauth를 IP로 노출하는 경우, {HYPERAUTH_EXTERNAL_IP} 세팅, dnsName 부분 전체 삭제
      * Hyperauth를 DNS로 노출하는 경우, {HYPERAUTH_EXTERNAL_DNS} 세팅, ipAddresses 부분 전체 삭제
  *  [hyperauth_certs.yaml](manifest/hyperauth_certs.yaml) 실행 `ex) kubectl apply -f hyperauth_certs.yaml`)
  *  Hyperauth Namespace에 hyperauth-https-secret이 생성된걸 확인한다.
```bash
    $ kubectl get secrets -n hyperauth
 ``` 	 	  		 	
 	* hyperauth-https-secret으로 부터 root-ca, hyperauth 인증서를 추출해서 kubernetes pki 에 위치한다.
```bash
    $ kubectl get secret hyperauth-https-secret -n hyperauth -o jsonpath="{['data']['tls\.crt']}" | base64 -d > ./hyperauth.crt
    $ kubectl get secret hyperauth-https-secret -n hyperauth -o jsonpath="{['data']['ca\.crt']}" | base64 -d > ./hypercloud-root-ca.crt
    $ cp ./hyperauth.crt /etc/kubernetes/pki/hyperauth.crt
    $ cp ./hypercloud-root-ca.crt /etc/kubernetes/pki/hypercloud-root-ca.crt
 ``` 
* 비고 :
  * Kubernetes Master가 다중화 된 경우, hypercloud-root-ca.crt, hyperauth.crt를 각 Master 노드들의 /etc/kubernetes/pki/hypercloud-root-ca.crt, /etc/kubernetes/pki/hyperauth.crt 로 cp

### Step 3. HyperAuth Deployment 배포
* 목적 : `HyperAuth 설치`
  * 생성 순서 : 
    * External-OIDC-provider 또는 외부 인증자와의 mTLS를 사용하지 않을 시, env에서 아래와 같은 주석을 가진 truststore/keystore 관련 설정을 삭제
    ```yaml
    # Enable If use External-oidc-provider or use mTLS (for initech)
    ```
  * [2.hyperauth_deployment.yaml](manifest/2.hyperauth_deployment.yaml) 실행 `ex) kubectl apply -f 2.hyperauth_deployment.yaml`
  * HyperAuth Admin Console에 접속 확인
    * `kubectl get svc hyperauth -n hyperauth` 명령어로 IP 확인
    * 계정 : admin/admin
* 비고 :
  * K8s admin 기본 계정 정보 : hc-admin@tmax.co.kr/Tmaxadmin1!
  * HyperAuth User 메뉴에서 비밀번호는 변경 가능, ID를 위해서는 clusterrole도 변경 필요

### Step 4. Client 등록
* 목적 : `HyperAuth 와 연동할 Client 등록`
  * 생성된 Client에서 사용하는 Credential로 Client에 client_secret을 등록  
  * Step 5에서 import한 초기 설정파일에는 다음과 같은 client들이 기 생성되어있다. 
    * hypercloud5, hyperregistry, gitlab, argocd, grafana
  * 해당 client module의 install guide에 따라서 oidc 연동을 수행한다.
    * hyperauth v2 에서는 oidc 연동 url이 다음과 같이 변경됨.
      * auth url : {hyperauth-external-dns}__/realms/tmax/protocol/openid-connect/auth__
      * token url : {hyperauth-external-dns}__/realms/tmax/protocol/openid-connect/token__
      * userinfo url : hyperauth-external-dns}__/realms/tmax/protocol/openid-connect/userinfo__ 
    * client 별로 생성된 credential을 사용하여 oidc 연동을 수행한다.

## 기타
### Ingress를 사용해서 hyperauth를 노출하려고 하는 경우
* hyperauth_traefik_ingress.yaml 에서 host 및 hosts를 환경에 맞는 dns로 수정하고 apply한다.
* 모든 마스터 노드에 관해서 self-signed 인증서의 경우, os의 ca store에 등록하는 과정을 거쳐야 k8s가 공인 인증서로 써 신뢰한다.
  * hypercloud-root-ca.crt, hyperauth.crt를 /etc/pki/ca-trust/source/anchors/ 밑에 복사한다. (centOS 기준)
  * update-ca-trust
 
### External-OIDC-Provider 사용
* 목적 : `External-OIDC-Provider 연동 또는 외부 인증자와의 mTLS 통신을 위한 keystore, truststore를 생성하고 secret으로 변환, truststore/keystore는 pkcs12 로 생성한다`
* 생성 순서 :
  1. [Truststore]
     * External-OIDC-Provider : Provider가 기 설치되어있어야 하며, 설치를 통해 생성된 external-oidc-provider-https-secret의 truststore.p12를 이용한다. 
     * 기타 외부 인증 서버 : 인증서버의 truststore.p12를 사용한다.
  2. [keystore]
     * Step 2.SSL 인증서 생성 에서 생성한 hyperauth.crt를 이용하여 keystore.p12를 생성한다.
     ```bash
         $  keytool -importcert -file hyperauth.crt -keystore keystore.p12 -alias hyperauth_keystore
     ```
     * keystore 생성 시 비밀번호를 입력하게 되는데, 해당 비밀번호를 기억해둔다.
     * 생성된 keystore.p12를 secret으로 변환한다.
     ```bash  
         $  kubectl create secret generic hyperauth-keystore -n hyperauth --from-file=keystore.p12 
     ```
  3. 2.Deployment에 mTLS 설정
      * 2에서 생성한 keystore의 비밀번호를 env로 설정
    ```yaml
      - name: KC_HTTPS_KEY_STORE_PASSWORD
        value: "keystore_password"
    ```
    
