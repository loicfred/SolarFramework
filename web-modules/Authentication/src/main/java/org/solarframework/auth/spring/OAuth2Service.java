package org.solarframework.auth.spring;

import org.solarframework.auth.obj.Account_User;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
public class OAuth2Service extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(request);
        String email = oauthUser.getAttribute("email");
        String firstName = oauthUser.getAttribute("given_name");
        String lastName = oauthUser.getAttribute("family_name");

        String provider = request.getClientRegistration().getRegistrationId();

        Account_User user = Account_User.getByEmail(email);
        if (user == null) {
            user = new Account_User();
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmailVerified(true);
            user.setPasswordHash(UUID.randomUUID().toString());
            //user.AccountProvider = provider;
            user.Write();
        }
        return new DefaultOAuth2User(Collections.emptyList(), oauthUser.getAttributes(), "email");
    }

}
