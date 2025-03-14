/*
 * Copyright (c) 2024-2025, Nirav Pistolwala
 * All rights reserved.
 *
 * New features and modifications in this project are licensed under the same
 * terms as the original code below.
 *
 */
// Copyright (c) 2018, Yubico AB
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this
//    list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
// OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package demo;

import demo.webauthn.WebAuthnRestResource;
import demo.webauthn.WebAuthnServer;

import javax.ws.rs.core.Application;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class App extends Application {

  private final Set<Class<?>> classes = new HashSet<>();
  private final Set<Object> singletons = new HashSet<>();

  public App(WebAuthnServer webAuthnServer) {
    classes.add(WebAuthnRestResource.class);
    singletons.add(new WebAuthnRestResource(webAuthnServer));
  }

  @Override
  public Set<Class<?>> getClasses() {
    return new HashSet<>(Collections.singletonList(CorsFilter.class));
  }

  @Override
  public Set<Object> getSingletons() {
    return singletons;
  }
}
