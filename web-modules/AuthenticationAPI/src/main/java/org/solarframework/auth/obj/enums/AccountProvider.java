package org.solarframework.auth.obj.enums;

public enum AccountProvider {

    GOOGLE(
            "google",
            "Google",
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            "https://openidconnect.googleapis.com/v1/userinfo",
            "openid email profile",
            true
    ),

    GITHUB(
            "github",
            "GitHub",
            "https://github.com/login/oauth/authorize",
            "https://github.com/login/oauth/access_token",
            "https://api.github.com/user",
            "read:user user:email",
            false
    ),

    MICROSOFT(
            "microsoft",
            "Microsoft",
            "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            "https://graph.microsoft.com/oidc/userinfo",
            "openid email profile User.Read",
            true
    ),

    APPLE(
            "apple",
            "Apple",
            "https://appleid.apple.com/auth/authorize",
            "https://appleid.apple.com/auth/token",
            null,
            "openid email name",
            true
    ),

    FACEBOOK(
            "facebook",
            "Facebook",
            "https://www.facebook.com/v18.0/dialog/oauth",
            "https://graph.facebook.com/v18.0/oauth/access_token",
            "https://graph.facebook.com/me",
            "email public_profile",
            false
    ),

    TWITTER(
            "twitter",
            "X / Twitter",
            "https://twitter.com/i/oauth2/authorize",
            "https://api.twitter.com/2/oauth2/token",
            "https://api.twitter.com/2/users/me",
            "tweet.read users.read offline.access",
            false
    ),

    DISCORD(
            "discord",
            "Discord",
            "https://discord.com/oauth2/authorize",
            "https://discord.com/api/oauth2/token",
            "https://discord.com/api/users/@me",
            "identify email",
            false
    ),

    LINKEDIN(
            "linkedin",
            "LinkedIn",
            "https://www.linkedin.com/oauth/v2/authorization",
            "https://www.linkedin.com/oauth/v2/accessToken",
            "https://api.linkedin.com/v2/userinfo",
            "openid profile email",
            true
    ),

    AMAZON(
            "amazon",
            "Amazon",
            "https://www.amazon.com/ap/oa",
            "https://api.amazon.com/auth/o2/token",
            "https://api.amazon.com/user/profile",
            "profile",
            false
    ),

    SLACK(
            "slack",
            "Slack",
            "https://slack.com/oauth/v2/authorize",
            "https://slack.com/api/oauth.v2.access",
            "https://slack.com/api/users.identity",
            "identity.basic identity.email",
            false
    ),

    GITLAB(
            "gitlab",
            "GitLab",
            "https://gitlab.com/oauth/authorize",
            "https://gitlab.com/oauth/token",
            "https://gitlab.com/api/v4/user",
            "read_user",
            false
    );


    private final String id;
    private final String displayName;
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String scopes;
    private final boolean openIdConnect;


    AccountProvider(
            String id,
            String displayName,
            String authorizationUrl,
            String tokenUrl,
            String userInfoUrl,
            String scopes,
            boolean openIdConnect
    ) {
        this.id = id;
        this.displayName = displayName;
        this.authorizationUrl = authorizationUrl;
        this.tokenUrl = tokenUrl;
        this.userInfoUrl = userInfoUrl;
        this.scopes = scopes;
        this.openIdConnect = openIdConnect;
    }


    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public String getUserInfoUrl() {
        return userInfoUrl;
    }

    public String getScopes() {
        return scopes;
    }

    public boolean isOpenIdConnect() {
        return openIdConnect;
    }
}