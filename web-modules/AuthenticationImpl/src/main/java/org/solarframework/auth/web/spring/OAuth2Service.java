package org.solarframework.auth.web.spring;

import org.solarframework.auth.obj.Account_User;
import org.solarframework.auth.obj.enums.AccountProvider;
import org.solarframework.auth.obj.enums.UserStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

import static org.solarframework.core.util.NumberUtils.GenerateRandomNumber;

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
            String normalizedFirstName = normalizeToAscii(firstName != null ? firstName : "");
            String normalizedLastName = normalizeToAscii(lastName != null ? lastName : "");
            String username = (normalizedFirstName + normalizedLastName + GenerateRandomNumber(1,10000)).toLowerCase();

            user = new Account_User(username, email, UUID.randomUUID().toString());
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEmailVerified(true);
            user.setStatus(UserStatus.ACTIVE);
            user.setAccountProvider(AccountProvider.GOOGLE);
            user.Write();
        }
        return new DefaultOAuth2User(Collections.emptyList(), oauthUser.getAttributes(), "email");
    }

    private String normalizeToAscii(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("[^a-zA-Z]", "");
    }

}
