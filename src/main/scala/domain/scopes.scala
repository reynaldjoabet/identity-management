package example.domain

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.github.iltotore.iron.constraint.string.*
import io.github.iltotore.iron.constraint.collection.*

object scopes {
  /*
   * RFC 6749 scope-token:
   *
   *   scope-token = 1*( %x21 / %x23-5B / %x5D-7E )
   *
   * This allows:
   *   ! # $ % & ' ( ) * + , - . / 0-9 : ; < = > ? @ A-Z [ ] ^ _ ` a-z { | } ~
   *
   * It excludes:
   *   space, double quote, backslash, control chars
   */
  type Rfc6749ScopeToken =
    Match["^[!#-\\[\\]-~]{1,128}$"]

  /*
   * Production hardening:
   * - no leading/trailing spaces
   * - no double spaces
   * - max 64 scope tokens in one request
   * - max 128 chars per token
   */
  type Rfc6749ScopeParameter =
    Match["^[!#-\\[\\]-~]{1,128}( [!#-\\[\\]-~]{1,128}){0,63}$"]

  type ScopeToken =
    String :| Rfc6749ScopeToken

  type ScopeParameter =
    String :| Rfc6749ScopeParameter

  type NonEmptyScopeSet =
    Set[Scope] :| MinLength[1]

  enum ScopeError {
    case Empty
    case InvalidScopeParameter(value: String)
    case InvalidScopeToken(value: String)
    case DuplicateScope(token: String)
    case UnknownScope(token: String)
  }

  enum OidcScope(val token: ScopeToken) {
    case OpenId extends OidcScope("openid")
    case Profile extends OidcScope("profile")
    case Email extends OidcScope("email")
    case Address extends OidcScope("address")
    case Phone extends OidcScope("phone")
    case OfflineAccess extends OidcScope("offline_access")
  }

  enum BankingScope(val token: ScopeToken) {
    case AccountsRead extends BankingScope("ob:accounts:read")
    case BalancesRead extends BankingScope("ob:balances:read")
    case TransactionsRead extends BankingScope("ob:transactions:read")
    case BeneficiariesRead extends BankingScope("ob:beneficiaries:read")
    case DirectDebitsRead extends BankingScope("ob:direct-debits:read")
    case StandingOrdersRead extends BankingScope("ob:standing-orders:read")
    case ScheduledPaymentsRead extends BankingScope("ob:scheduled-payments:read")
    case FundsConfirmationsRead extends BankingScope("ob:funds-confirmations:read")

    case PaymentInitiationWrite extends BankingScope("ob:payments:write")
    case VrpWrite extends BankingScope("ob:vrp:write")
  }

  enum Api1Scope(val token: ScopeToken) {
    case CustomersRead extends Api1Scope("api1:customers:read")
    case CustomerRiskRead extends Api1Scope("api1:customer-risk:read")
    case ReportsRead extends Api1Scope("api1:reports:read")
    case WebhooksWrite extends Api1Scope("api1:webhooks:write")
    case ServiceAccountsRead extends Api1Scope("api1:service-accounts:read")
  }

  enum PlatformScope(val token: ScopeToken) {
    case ClientsRead extends PlatformScope("admin:clients:read")
    case ClientsWrite extends PlatformScope("admin:clients:write")
    case JwksRotate extends PlatformScope("admin:jwks:rotate")
    case AuditRead extends PlatformScope("admin:audit:read")
  }

  val scop = PlatformScope.AuditRead

  enum Scope {
    case Oidc(value: OidcScope)
    case Banking(value: BankingScope)
    case Api1(value: Api1Scope)
    case Platform(value: PlatformScope)
    case Custom(value: ScopeToken)

    def token: ScopeToken =
      this match {
        case Scope.Oidc(value)     => value.token
        case Scope.Banking(value)  => value.token
        case Scope.Api1(value)     => value.token
        case Scope.Platform(value) => value.token
        case Scope.Custom(value)   => value
      }
  }

  private val known: Map[ScopeToken, Scope] = {
    val oidc =
      OidcScope.values.map(scope => scope.token -> Scope.Oidc(scope))

    val banking =
      BankingScope.values.map(scope => scope.token -> Scope.Banking(scope))

    val api1 =
      Api1Scope.values.map(scope => scope.token -> Scope.Api1(scope))

    val platform =
      PlatformScope.values.map(scope => scope.token -> Scope.Platform(scope))

    (oidc ++ banking ++ api1 ++ platform).toMap
  }

  def fromToken(
      token: ScopeToken,
      allowCustomScopes: Boolean
  ): Either[ScopeError, Scope] = {
    known.get(token) match {
      case Some(scope) =>
        Right(scope)

      case None if allowCustomScopes =>
        Right(Scope.Custom(token))

      case None =>
        Left(ScopeError.UnknownScope(token.toString))
    }
  }

}
import scopes.*

val valid: ScopeToken =
  "ob:accounts:read"

// Does not compile:
//
//val invalid: ScopeToken = "accounts read"
//
// because spaces are not valid inside one scope token.

//Scala 3 does give you singleton types for parameterless enum cases:

object example {
  def f(s: BankingScope.BalancesRead.type): Unit = ???

  f(BankingScope.BalancesRead) // compiles
// f(BankingScope.BeneficiariesRead) // does NOT compile

//Authorization is fundamentally a runtime set-membership question (the token is data arriving over the wire), so a compile-time singleton buys you nothing at the actual decision point.

}
