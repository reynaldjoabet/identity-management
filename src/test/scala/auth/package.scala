package com.fintech

given CanEqual[org.http4s.Method, org.http4s.Method] = CanEqual.derived
given CanEqual[org.http4s.Uri.Path, org.http4s.Uri.Path] = CanEqual.derived
