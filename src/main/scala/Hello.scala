package example

import java.security.PKCS12Attribute

import java.security.spec
import java.security.interfaces

import java.security.cert
import java.security.Signature
import java.security.Security
import java.security.SecureRandomParameters
import java.security.PublicKey
import java.security.PrivateKey
import java.security.MessageDigest
import java.security.KeyStore
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.KeyFactorySpi //The provider implementation
import java.security.KeyFactory
import java.security

object Hello extends Greeting with App {
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}
