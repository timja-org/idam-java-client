package uk.gov.hmcts.reform.idam.client;

import java.net.MalformedURLException;
import java.net.URL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.idam.client.models.AuthenticateUserRequest;
import uk.gov.hmcts.reform.idam.client.models.AuthenticateUserResponse;
import uk.gov.hmcts.reform.idam.client.models.ExchangeCodeRequest;
import uk.gov.hmcts.reform.idam.client.models.TokenExchangeResponse;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;

import java.util.Base64;

@Service
public class IdamClient {

    public static final String AUTH_TYPE = "code";
    public static final String GRANT_TYPE = "authorization_code";
    public static final String BASIC_AUTH_TYPE = "Basic";
    public static final String BEARER_AUTH_TYPE = "Bearer";

    private IdamApi idamApi;
    private final OAuth2Configuration oauth2Configuration;
    private final JWKSource jwkSource;
    private final JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;

    private ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();

    @Autowired
    public IdamClient(IdamApi idamApi, OAuth2Configuration oauth2Configuration, @Value("idam.api.url") String idamApiUrl) {
        this.idamApi = idamApi;
        this.oauth2Configuration = oauth2Configuration;

        try {
            keySource = new RemoteJWKSet(new URL(idamApiUrl + "/jwks"));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid idam URL", e);
        }
    }

    public UserDetails getUserDetails(String bearerToken) {
        JWSKeySelector keySelector = new JWSVerificationKeySelector(expectedJWSAlg, jwkSource);
        jwtProcessor.setJWSKeySelector(keySelector);
// Process the token
        SecurityContext ctx = null; // optional context parameter, not required here
        JWTClaimsSet claimsSet = jwtProcessor.process(accessToken, ctx);

        // TODO figure out next bit

        return idamApi.retrieveUserDetails(bearerToken);
    }

    public String authenticateUser(String username, String password) {
        String authorisation = username + ":" + password;
        String base64Authorisation = Base64.getEncoder().encodeToString(authorisation.getBytes());

        String clientId = oauth2Configuration.getClientId();

        String redirectUri = oauth2Configuration.getRedirectUri();

        AuthenticateUserResponse authenticateUserResponse = idamApi.authenticateUser(
            BASIC_AUTH_TYPE + " " + base64Authorisation,
            new AuthenticateUserRequest(AUTH_TYPE, clientId, redirectUri)
        );

        ExchangeCodeRequest exchangeCodeRequest = new ExchangeCodeRequest(authenticateUserResponse
            .getCode(), GRANT_TYPE, redirectUri, clientId, oauth2Configuration.getClientSecret());

        TokenExchangeResponse tokenExchangeResponse = idamApi.exchangeCode(exchangeCodeRequest);

        return BEARER_AUTH_TYPE + " " + tokenExchangeResponse.getAccessToken();
    }

}