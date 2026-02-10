# External Provider

The workflow using an external provider is much like the workflow from one of your client applications using your IdentityServer. Your login page must redirect the user to the identity provider for login, and the identity provider will redirect the user to a callback endpoint in your IdentityServer to process the results. This means the external provider should implement a standard protocol (e.g. Open ID Connect, SAML2-P, or WS-Federation) to allow such an integration
To ease integration with external providers, it is recommended to use an authentication handler for ASP.NET Core that implements the corresponding protocol used by the provider.

## Registering Authentication Handlers For External Providers
Supporting an external provider is achieved by registering the handler in your IdentityServer’s startup configuration.

```c#
builder.Services.AddIdentityServer();

builder.Services.AddAuthentication()
    .AddOpenIdConnect("AAD", "Employee Login", options =>
    {
        // options omitted
    });
```    

`/sso-external-login` sso initiation endpoint

- clientname
- providername
-return url

validats if the identity provider exists for client
- creates dynamic authentication scheme metadata

redirecturi= /sso-external-login-callback

`GetIdentityProviderByNameAsync(string ClientName,string provider)`
and use it to build dynamic scheme
`var externalLoginResult=await GetHttpContext().AuthenticateAsync(IdentityServerConstants.ExternalAuthenticationScheme)`

AuthenticateSsoAsync

how can sso authentication fail after succcessful external login?


`GetIdentityProviderByNameAsync(string ClientName,string provider)`


`ChallengeResult` is a built-in class from AspNetScore.Mvc that
- Inititiates an Authentication challenge with a specific authentication scheme
- Redirects the user to an external authentication provider
- Preserves state through `AuthenticationProperties` so the app knows where to return after authentication

## Authentication Handlers
Authentication is implemented using Authentication Handlers.

What they do
- Validate credentials (JWT, cookies, OAuth tokens, API keys, etc.)
- Create a ClaimsPrincipal
- Set HttpContext.User

Common examples
- JwtBearerHandler
- CookieAuthenticationHandler
- OAuthHandler<TOptions>
- OpenIdConnectHandler

After authentication runs, `HttpContext.User.Identity.IsAuthenticated == true`

## Authorization Handlers

Authorization is implemented using Authorization Handlers.

What they do
- Evaluate requirements against the authenticated user
- Decide Allow / Deny

Usually implemented via:
`AuthorizationHandler<TRequirement>`

The `Google` authentication provider relies on the ability to issue a cookie, which stores our `ClaimsPrincipal`. If not, the application would redirect the user to our external provider every time they make a request, leading to a frustrating experience. The cookie provides a persistent state during a user’s session.  

The `Google` handler only implements the google protocol.. 
Meaning like how to get to Google and how to validate what comes from Google. After that it need, it needs to start a session for that user. The session managment part is not part of the Google handler, it is already implemented in the cookie handler. The `Google` handler will handoff all of the session management to another handler once the job is done. You specify which handler using the `SignInScheme`
```c#
builder.Services.AddAuthentication(CookieAuthenticationDefaults.AuthenticationScheme)
    .AddCookie("cookie,o=>{
    o.Cookie.Name="Demo";
    o.ExpiredTimeSpan=TimeSpan.fromHours(8);
    o.LoginPath="/account/login";
    o.AccessDeniedpath="/account/accessdenied";

    }).AddGoogle("Google",o=>{
        o.ClientId="";
        o.ClientSecret= "";
        o.SignInScheme="cookie"// this says, once you are done, call a handler named cookie to sign in the user and start the session
    })
   builder.Services  .AddOpenIdConnect("Auth0Scheme", "Auth0", options =>
        {
            options.SignInScheme = IdentityServerConstants.ExternalCookieAuthenticationScheme;
            options.SignOutScheme = IdentityConstants.ApplicationScheme;
            options.CallbackPath = new PathString("/signin-oidc-auth0");
            options.RemoteSignOutPath = new PathString("/signout-callback-oidc-auth0");
            options.SignedOutCallbackPath = new PathString("/signout-oidc-auth0");

            options.Authority = $"https://{builder.Configuration["Auth0:Domain"]}";
            options.ClientId = builder.Configuration["Auth0:ClientId"];
            options.ClientSecret = builder.Configuration["Auth0:ClientSecret"];
            options.ResponseType = OpenIdConnectResponseType.Code;
            options.Scope.Clear();
            options.Scope.Add("openid");
            options.Scope.Add("profile");
            options.Scope.Add("email");
            options.Scope.Add("auth0-user-api-one");
                
            options.ClaimsIssuer = "Auth0";
            options.SaveTokens = true;
            options.UsePkce = true;
            options.GetClaimsFromUserInfoEndpoint = true;
            options.TokenValidationParameters.NameClaimType = "name";
            options.Events = new OpenIdConnectEvents
            {
                OnTokenResponseReceived = context =>
                {
                    var idToken = context.TokenEndpointResponse.IdToken;
                    return Task.CompletedTask;
                }
            };

            options.Events = new OpenIdConnectEvents
            {
                // handle the logout redirection 
                OnRedirectToIdentityProviderForSignOut = (context) =>
                {
                    var logoutUri = $"https://{builder.Configuration["Auth0:Domain"]}/v2/logout?client_id={builder.Configuration["Auth0:ClientId"]}";

                    var postLogoutUri = context.Properties.RedirectUri;
                    if (!string.IsNullOrEmpty(postLogoutUri))
                    {
                        if (postLogoutUri.StartsWith("/"))
                        {
                            // transform to absolute
                            var request = context.Request;
                            postLogoutUri = request.Scheme + "://" + request.Host + request.PathBase + postLogoutUri;
                        }
                        logoutUri += $"&returnTo={Uri.EscapeDataString(postLogoutUri)}";
                    }

                    context.Response.Redirect(logoutUri);
                    context.HandleResponse();

                    return Task.CompletedTask;
                },
                OnRedirectToIdentityProvider = context =>
                {
                    // The context's ProtocolMessage can be used to pass along additional query parameters
                    // to Auth0's /authorize endpoint.
                    // 
                    // Set the audience query parameter to the API identifier to ensure the returned Access Tokens can be used
                    // to call protected endpoints on the corresponding API.
                    context.ProtocolMessage.SetParameter("audience", "https://auth0-api1");
                    context.ProtocolMessage.AcrValues = "http://schemas.openid.net/pape/policies/2007/06/multi-factor";

                    return Task.FromResult(0);
                }
            };
        })

 ``` 
## The Better way to work with External Providers

While the article has shown you how external providers work with ASP.NET Core, the approach outlined so far can become problematic as the number of solutions within your organization grows. If you manage a single application, the current solution’s complexity will be low. The complexity intensifies as you add more applications, APIs, and background services. The need to manage the same authentication code across multiple projects hinders productivity and limits what is possible.

That is where Duende IdentityServer can significantly simplify authentication. Using Duende IdentityServer, you set up your external providers once at a single identity provider and federate user identity to all relying parties. You can maintain and evolve authentication code in one location while relying parties benefit from relying solely on the OpenID Connect specification.

In practice, federating through a single Identity Provider, such as Duende IdentityServer, allows you to add and remove external providers without affecting downstream relationships. It also allows you to transform and supplement claims that may not be provided initially by the external provider

Using external login providers (such as Google, Microsoft, or Azure AD) directly in an ASP.NET Core application works well when you have only one application. All authentication logic lives in that single project, so it is easy to understand and maintain.
Problems appear when your organization grows.
As you add more web applications, APIs, and background services, each project needs to:
- Configure the same external providers
- Maintain the same authentication logic
- Handle updates, fixes, and security changes independently
This leads to duplicated code, inconsistent behavior, and higher maintenance costs. Every change—such as adding a new provider or updating security requirements—must be repeated across multiple projects.
Duende IdentityServer solves this by acting as a central authentication service.

## Without Duende IdentityServer (Direct Integration)
Scenario
Your company has:
- A Customer Portal (ASP.NET Core MVC)
- An Admin Portal (ASP.NET Core MVC)
- A Web API
- A Background Worker
Each project integrates `Google` and `Microsoft` login directly.
### What this looks like
Each application must:
- Register its own Google and Microsoft app
- Store client IDs and secrets
- Configure authentication middleware
- Handle login callbacks
- Manage claims mapping and tokens
```c#
builder.Services.AddAuthentication()
    .AddGoogle(options =>
    {
        options.ClientId = "...";
        options.ClientSecret = "...";
    })
    .AddMicrosoftAccount(options =>
    {
        options.ClientId = "...";
        options.ClientSecret = "...";
    });
 ```   

### Problems
- Same configuration repeated in every project
- Adding a new provider means updating every app
- Small differences cause inconsistent claims or behavior
- Harder to enforce security policies consistently

## With Duende IdentityServer (Centralized Authentication)
Scenario
Duende IdentityServer is set up as a single identity provider.
### What changes
- `Google` and `Microsoft` login are configured once in IdentityServer
- Applications do not talk to external providers directly
Applications trust IdentityServer via OpenID Connect
- IdentityServer configuration (one time)
```c#
builder.Services.AddAuthentication()
    .AddGoogle("Google", options =>
    {
        options.SignInScheme =
            IdentityServerConstants.ExternalCookieAuthenticationScheme;
        options.ClientId = Configuration["Authentication:Google:ClientId"];
        options.ClientSecret = Configuration["Authentication:Google:ClientSecret"];
    })
    .AddMicrosoftAccount("Microsoft", options =>
    {
        options.SignInScheme =
            IdentityServerConstants.ExternalCookieAuthenticationScheme;
        options.ClientId = Configuration["Authentication:Microsoft:ClientId"];
        options.ClientSecret = Configuration["Authentication:Microsoft:ClientSecret"];
    });

```    
Application configuration (simple and consistent)

```c#
builder.Services.AddAuthentication(options =>
{
    options.DefaultScheme = "Cookies";
    options.DefaultChallengeScheme = "oidc";
})
.AddOpenIdConnect("oidc", options =>
{
    options.Authority = "https://identity.company.com";
    options.ClientId = "customer-portal";
    options.ClientSecret = "...";
});
```
### Benefits
- External providers live in one place
- Applications share the same identity model
- Adding a new provider requires zero changes to apps
- Centralized security and policy enforcement

## Adding a New External Provider
### Without IdentityServer
- Register the provider for every app
- Update configuration in every project
- Test each integration separately
### With IdentityServer
- Register the provider once
- All applications support it automatically

## Claims and User Data Consistency
### Without IdentityServer
- Google returns different claims than Microsoft
- Each app maps claims differently
- User identity varies per application
### With IdentityServer
- IdentityServer normalizes claims
- Applications receive consistent identity data
APIs and services trust the same user identity

IdentityServer: register external Google provider (one-time)
// IdentityServer Startup / Program.cs
```c#
builder.Services.AddAuthentication()
  .AddGoogle("Google", options =>
  {
      options.SignInScheme = IdentityServerConstants.ExternalCookieAuthenticationScheme;
      options.ClientId = Configuration["Authentication:Google:ClientId"];
      options.ClientSecret = Configuration["Authentication:Google:ClientSecret"];
      options.Scope.Add("email");
      options.Scope.Add("profile");
  });
```  

IdentityServer: client (application) config
```c#
new Client
{
    ClientId = "customer-portal",
    ClientName = "Customer Portal",
    AllowedGrantTypes = GrantTypes.Code,
    RequirePkce = true,
    RedirectUris = { "https://customer.app/signin-oidc" },
    PostLogoutRedirectUris = { "https://customer.app/signout-callback-oidc" },
    AllowedScopes = { "openid", "profile", "email", "api.read" },
    ClientSecrets = { new Secret("super-secret".Sha256()) }
};

```
Application: use OIDC to login
```c#
builder.Services.AddAuthentication(options =>
{
    options.DefaultScheme = CookieAuthenticationDefaults.AuthenticationScheme;
    options.DefaultChallengeScheme = "oidc";
})
.AddCookie()
.AddOpenIdConnect("oidc", options =>
{
    options.Authority = "https://identity.company.com";
    options.ClientId = "customer-portal";
    options.ClientSecret = Configuration["Identity:CustomerPortalSecret"];
    options.ResponseType = "code";
    options.SaveTokens = true;
    options.GetClaimsFromUserInfoEndpoint = true;
});
```

API: validate tokens issued by IdentityServer
```c#
builder.Services.AddAuthentication("Bearer")
 .AddJwtBearer("Bearer", options =>
 {
     options.Authority = "https://identity.company.com";
     options.Audience = "api";
     options.RequireHttpsMetadata = true;
 });
``` 
Duende IdentityServer provides this by acting as a federation gateway, allowing applications to rely solely on standard OpenID Connect interactions while the complexity of external providers is handled in one place.

Single Sign-On means:
A user authenticates once and can then access multiple applications without logging in again.
The key requirement is:
A shared identity authority that all applications trust

What “Single Logout” (SLO) means
Single Logout means:
Logging out from one application logs the user out of all applications.
`GET https://identity.company.com/connect/endsession`

### Back-channel / front-channel logout (optional but important)
IdentityServer may now:
Notify other applications that participated in the session
- Front-channel logout (browser-based)
- Back-channel logout (server-to-server)

Each app:
Receives a logout notification
Clears its own cookie

**User is redirected back**
IdentityServer redirects the user back to:
App A Or a global logged-out page
User is now logged out everywhere.

## Front-channel logout (simplest)
How it works
- User initiates logout
- IdentityServer renders a page with invisible <iframe>s
- Each iframe points to a registered logout URL for an app
- Browser loads those URLs
- Each app clears its cookie

Each app exposes a front-channel logout endpoint:
`https://appA.com/signout-oidc`

## Cons
- Requires the browser
- Fails if browser blocks third-party cookies or iframes
- Not reliable for APIs

## Back-channel logout (recommended for robustness)
How it works
User logs out
- IdentityServer sends server-to-server HTTP POSTs
- Each app receives a signed logout token
- App clears sessions matching that token

Each app exposes a back-channel logout endpoint:
POST `https://appA.com/backchannel-logout`

The request contains:
- A signed JWT
- Session identifiers
- Client ID
- Issuer (IdentityServer)
App must:
- Validate the JWT
- Locate the session
- Terminate it

### Pros
- Reliable
- Works for APIs and SPAs
Not affected by browser behavior
### Cons
- More complex to implement
- Requires session tracking


## Federation Gateway

Federation means that your IdentityServer offers authentication methods that use external authentication providers. When you offer a number of these external authentication methods, often the term Federation Gateway is used to describe this architectural approach.

```c#
builder.Services.AddIdentityServer();

builder.Services.AddAuthentication()
    .AddOpenIdConnect("AAD", "Employee Login", options =>
    {
        // options omitted
    });
```

The above snippet registers a scheme called `AAD` in the ASP.NET Core authentication system, and uses a human-friendly display name of “Employee Login”.

The process of determining which identity provider to use is called Home Realm Discovery, or `HRD` for short.

To invoke an external authentication handler use the `ChallengeAsync` extension method on the `HttpContext` (or using the MVC `ChallengeResult`). When triggering challenge, it’s common to pass some properties to indicate the callback URL where you intend to process the external login results and any other state you need to maintain across the workflow (e.g. such as the return URL passed to the login page):

```c#
var callbackUrl = Url.Action("MyCallback");

var props = new AuthenticationProperties
{
    RedirectUri = callbackUrl,
    Items =
    {
        { "scheme", "AAD" },
        { "returnUrl", returnUrl }
    }
};

return Challenge("AAD", props);
```

`SignInScheme` is always cookie based
This is why you commonly see:
```c#
options.SignInScheme = CookieAuthenticationDefaults.AuthenticationScheme;
```
or, in IdentityServer:
```c#
options.SignInScheme =
    IdentityServerConstants.ExternalCookieAuthenticationScheme;
```    

One option on external authentication handlers is called `SignInScheme`. This specifies the cookie handler to manage the state:

```c#
builder.Services.AddAuthentication()
    .AddOpenIdConnect("AAD", "Employee Login", options =>
    {
        options.SignInScheme = "scheme of cookie handler to use";

        // other options omitted
    });
 ```   

Given that this is such a common practice, IdentityServer registers a cookie handler specifically for this external provider workflow. The scheme is represented via the `IdentityServerConstants.ExternalCookieAuthenticationScheme` constant. If you were to use our external cookie handler, then for the `SignInScheme` above, you’d assign the value to be the `IdentityServerConstants.ExternalCookieAuthenticationScheme` constant: 

```c#
builder.Services.AddAuthentication()
    .AddOpenIdConnect("AAD", "Employee Login", options =>
    {
        options.SignInScheme = IdentityServerConstants.ExternalCookieAuthenticationScheme;

        // other options omitted
    });
 ```   

 ### Sign Out Scheme
 `SignInScheme` of the external provider should always be `IdentityServerConstants.ExternalCookieAuthenticationScheme`. The `SignOutScheme` depends on whether ASP.NET Identity is used or not:

 ```c#
 // Program.cs
 //With ASP.NET Identity
builder.Services.AddAuthentication()
    .AddCookie("MyTempHandler")
    .AddOpenIdConnect("AAD", "Employee Login", options =>
    {
        options.SignOutScheme = IdentityConstants.ApplicationScheme
        // other options omitted
    });
 ```   

```c#
//Without ASP.NET Identity
// Program.cs
builder.Services.AddAuthentication()
    .AddCookie("MyTempHandler")
    .AddOpenIdConnect("AAD", "Employee Login", options =>
    {
        options.SignOutScheme = IdentityServerConstants.SignoutScheme
        // other options omitted
    });
```     


### Handling The Callback

On the callback page your typical tasks are:

- Inspect the identity returned by the external provider.
- Make a decision how you want to deal with that user. This might be different based on if this is a new user or a returning user.
- New users might need additional steps and UI before they are allowed in. `Typically, this involves creating a new internal user account that is linked to the user from the external provider`.
- Store the external claims that you want to keep.
- Delete the temporary cookie.
- Establish the user’s authentication session.
- Complete the login workflow

The `sub` claim from the external cookie is the external provider’s unique id for the user. This value should be used to locate your local user record for the user.

```c#
// retrieve claims of the external user
var userId = externalUser.FindFirst("sub").Value;
```
Once your callback page logic has identified the user based on the external identity provider, it will log the user in and complete the original login workflow:
```c#
var user = FindUserFromExternalProvider(scheme, userId);

// issue authentication cookie for user
await HttpContext.SignInAsync(new IdentityServerUser(user.SubjectId)
{
    DisplayName = user.DisplayName,
    IdentityProvider = scheme
});

// delete temporary cookie used during external authentication
await HttpContext.SignOutAsync(IdentityServerConstants.ExternalCookieAuthenticationScheme);

// return back to protocol processing
return Redirect(returnUrl);
```
Typically, the `sub` value used to log the user in would be the user’s unique id from your local user database.

```c#
public class ApplicationUser : IdentityUser
{
    public string FavoriteColor { get; set; }
}

alice = new ApplicationUser
{
    UserName = "alice",
    Email = "AliceSmith@email.com",
    EmailConfirmed = true,
    FavoriteColor = "red",
};

```

Now that you have more data in the database, you can use it to set claims. IdentityServer contains an extensibility point called the `IProfileService` that is responsible for retrieval of user claims. The ASP.NET Identity Integration includes an implementation of `IProfileService` that retrieves claims from ASP.NET Identity. You can extend that implementation to use the custom profile data as a source of claims data

Create a new file called `src/IdentityServerAspNetIdentity/CustomProfileService.cs` and add the following code to it:

```c#
using Duende.IdentityServer.AspNetIdentity;
using Duende.IdentityServer.Models;
using IdentityServerAspNetIdentity.Models;
using Microsoft.AspNetCore.Identity;
using System.Security.Claims;

namespace IdentityServerAspNetIdentity
{
    public class CustomProfileService : ProfileService<ApplicationUser>
    {
        public CustomProfileService(UserManager<ApplicationUser> userManager, IUserClaimsPrincipalFactory<ApplicationUser> claimsFactory) : base(userManager, claimsFactory)
        {
        }

        protected override async Task GetProfileDataAsync(ProfileDataRequestContext context, ApplicationUser user)
        {
            var principal = await GetUserClaimsAsync(user);
            var id = (ClaimsIdentity)principal.Identity;
            if (!string.IsNullOrEmpty(user.FavoriteColor))
            {
                id.AddClaim(new Claim("favorite_color", user.FavoriteColor));
            }

            context.AddRequestedClaims(principal.Claims);
        }
    }
}
```


```c#
using Duende.IdentityServer;
using Duende.IdentityServer.Models;
using Microsoft.AspNetCore.Authentication.Cookies;

var builder = WebApplication.CreateBuilder(args);
var config = builder.Configuration;

// Basic IdentityServer (in-memory for demo)
builder.Services.AddIdentityServer(options =>
{
    options.Events.RaiseSuccessEvents = true;
    options.Events.RaiseFailureEvents = true;
    options.Events.RaiseErrorEvents = true;
})
    .AddInMemoryClients(new List<Client>
    {
        new Client
        {
            ClientId = "appA",
            ClientName = "App A",
            AllowedGrantTypes = GrantTypes.Code,
            RequirePkce = true,
            RequireClientSecret = false, // for public clients; set true + secret for confidential apps
            RedirectUris = { "https://localhost:5001/signin-oidc" },
            PostLogoutRedirectUris = { "https://localhost:5001/signout-callback-oidc" },
            FrontChannelLogoutUri = "https://localhost:5001/signout-oidc",
            BackChannelLogoutUri = "https://localhost:5001/backchannel-logout",
            AllowedScopes = { "openid", "profile", "email", "api" },
            AllowOfflineAccess = true
        },
        new Client
        {
            ClientId = "appB",
            ClientName = "App B",
            AllowedGrantTypes = GrantTypes.Code,
            RequirePkce = true,
            RequireClientSecret = false,
            RedirectUris = { "https://localhost:5002/signin-oidc" },
            PostLogoutRedirectUris = { "https://localhost:5002/signout-callback-oidc" },
            FrontChannelLogoutUri = "https://localhost:5002/signout-oidc",
            BackChannelLogoutUri = "https://localhost:5002/backchannel-logout",
            AllowedScopes = { "openid", "profile", "email", "api" },
            AllowOfflineAccess = true
        }
    })
    .AddInMemoryIdentityResources(new List<IdentityResource>
    {
        new IdentityResources.OpenId(),
        new IdentityResources.Profile(),
        new IdentityResources.Email()
    })
    .AddInMemoryApiScopes(new List<ApiScope>
    {
        new ApiScope("api", "Demo API")
    });

// Cookie used for SSO session (IdentityServer)
builder.Services.AddAuthentication(options =>
{
    // IdentityServer will handle default scheme internally; explicit cookie registration shown for clarity
}).AddCookie(IdentityServerConstants.DefaultCookieAuthenticationScheme, options =>
{
    options.Cookie.Name = "idsrv.session";            // primary SSO cookie
    options.Cookie.SameSite = SameSiteMode.Lax;
    options.Cookie.SecurePolicy = CookieSecurePolicy.Always;
});

// External cookie (temporary staging)
builder.Services.AddAuthentication()
    .AddCookie(IdentityServerConstants.ExternalCookieAuthenticationScheme, options =>
    {
        options.Cookie.Name = "idsrv.external";
        options.Cookie.SameSite = SameSiteMode.Lax;
        options.Cookie.SecurePolicy = CookieSecurePolicy.Always;
    });

// --------------------------
// External providers (Google + Microsoft)
// IMPORTANT: configure client ids/secrets via secrets or config
// --------------------------
builder.Services.AddAuthentication()
    .AddGoogle("Google", options =>
    {
        options.SignInScheme = IdentityServerConstants.ExternalCookieAuthenticationScheme;
        options.ClientId = config["Google:ClientId"];
        options.ClientSecret = config["Google:ClientSecret"];
        options.Scope.Add("profile");
        options.Scope.Add("email");
    })
    .AddMicrosoftAccount("Microsoft", options =>
    {
        options.SignInScheme = IdentityServerConstants.ExternalCookieAuthenticationScheme;
        options.ClientId = config["Microsoft:ClientId"];
        options.ClientSecret = config["Microsoft:ClientSecret"];
        // scopes as required
    });

// (Optional) Add UI + endpoints for interactive flows
builder.Services.AddRazorPages();

var app = builder.Build();

app.UseHttpsRedirection();
app.UseStaticFiles();

app.UseRouting();

app.UseIdentityServer();  // includes authentication middleware
app.UseAuthorization();

app.MapRazorPages();

// Example front-channel logout handler (renders iframe page)
app.MapGet("/logout-frontchannel-page", async (HttpContext http) =>
{
    // For demo: fetch clients that were in this session and render iframes.
    // IdentityServer has ways to track client sessions; here we render example iframes.
    var html = @"<html><body>
        <iframe src=""https://localhost:5001/signout-oidc"" style=""display:none""></iframe>
        <iframe src=""https://localhost:5002/signout-oidc"" style=""display:none""></iframe>
        Signed out.
        </body></html>";
    http.Response.ContentType = "text/html";
    await http.Response.WriteAsync(html);
});

app.Run();
```

RequestedClaimTypes is what the client asked for — not everything the user has.

```c#
// JWT Bearer
JwtBearerDefaults.AuthenticationScheme = "Bearer"

// Duende IdentityServer Cookies
IdentityServerConstants.DefaultCookieAuthenticationScheme = "idsrv"
IdentityServerConstants.ExternalCookieAuthenticationScheme = "idsrv.external"

public sealed class LogoutMode(int id, string name) : Enumeration(id, name)
{
    public static readonly LogoutMode ClientOnly = new(1, nameof(ClientOnly));
    public static readonly LogoutMode FullWithIframeRedirect = new(2, nameof(FullWithIframeRedirect));
    public static readonly LogoutMode FullWithFrontChannel = new(3, nameof(FullWithFrontChannel));
}

  var authenticationProperties = new AuthenticationProperties { RedirectUri = postLogoutUri };

        var frontChannelLogoutResult = new SignOutResult(signOutSchemes, authenticationProperties);
        var result = new ExternalLogoutResult(frontChannelLogoutResult, nameof(FullLogoutWithFrontChannel))

public Task<IExternalLogoutResult> LogoutAsync(...)
{
    var idpScheme = GetSchemeName(identityProviderDto);

    var signOutSchemes = new[]
    {
        IdentityServerConstants.DefaultCookieAuthenticationScheme,  // Local app
        idpScheme  // External IdP scheme (e.g., "acme::entraid")
    };

    var authenticationProperties = new AuthenticationProperties
    {
        RedirectUri = postLogoutUri
    };

    // Signs out from BOTH local app AND external IdP
    var frontChannelLogoutResult = new SignOutResult(signOutSchemes, authenticationProperties);

    return Task.FromResult(frontChannelLogoutResult);
}
{
    private const string SigninOidc = "/signin-oidc-im";
    private const string OidcLoginCallback = "/oidc/login-callback";
    private const string OidcFrontChannelLogoutCallback = "/oidc/front-channel-logout-callback";

PostLogoutRedirectUris = {home}
FrontChannelLogoutUri = home + OidcFrontChannelLogoutCallback,
FrontChannelLogoutSessionRequired = true

var postLogoutUri = await GetPostLogoutUriAsync(clientNamespace, cancellationToken);

var result = HttpContext.IsExternalAuthentication()
                ? await HandleSsoLogoutAsync(clientNamespace, postLogoutUri, cancellationToken)
                : await HandleNativeLogoutAsync(clientNamespace, postLogoutUri);

return result;
}
```    

Duende IdentityServer is a framework to build an OpenID Connect (OIDC) and OAuth 2.x standards-compliant authentication server using ASP.NET Core.

It is designed to provide a common way to authenticate requests to all of your applications, whether they're web, native, mobile, or API endpoints. IdentityServer can be used to implement Single Sign-On (SSO) for multiple applications and application types. It can be used to authenticate actual users via sign-in forms and similar user interfaces as well as service-based authentication that typically involves token issuance, verification, and renewal without any user interface. It can also act as a federation gateway to unify authentication providers.


```c#
builder.Services.AddAuthentication(options => {
    options.DefaultScheme = "Cookies";
    options.DefaultChallengeScheme = "oidc";
})
.AddCookie("Cookies")
.AddOpenIdConnect("oidc", options => {
    options.Authority = "https://your-duende-server.com";
    options.ClientId = "interactive_client";
    // ... other config ...

    options.Events = new OpenIdConnectEvents
    {
        OnRedirectToIdentityProvider = context =>
        {
            // 1. You can hardcode a value
            context.ProtocolMessage.AcrValues = "tenant:mandalore idp:Google";

            // 2. Or, more dynamically, pull from the current request's properties
            if (context.Properties.Items.TryGetValue("idp", out var idp))
            {
                context.ProtocolMessage.AcrValues = $"idp:{idp}";
            }

            return Task.CompletedTask;
        }
    };
});
```

### How to trigger it dynamically

If you don't want to hardcode the IDP but want to decide it at the moment the user clicks "Login," you can pass the value through the AuthenticationProperties when you call `Challenge`


```c#
// Inside a Controller action
public IActionResult Login(string provider)
{
    var props = new AuthenticationProperties
    {
        RedirectUri = "/",
        Items = { { "idp", provider } } // This is picked up by the event above
    };

    return Challenge(props, "oidc");
}
```

It looks for the configuration you defined in AddOpenIdConnect("oidc", ...) in your Program.cs. It gathers the Authority (your Duende URL), your `ClientId`, and your `RedirectUri`.

## OnRedirectToIdentityProvider

Just before the user is actually sent away, the event `OnRedirectToIdentityProvider` fires. This is where the props you passed into the `Challenge` method are used to inject extra logic (like `acr_values`)