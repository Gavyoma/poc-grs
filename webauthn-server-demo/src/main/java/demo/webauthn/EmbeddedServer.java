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

package demo.webauthn;

import demo.App;
import demo.webauthn.grs.GrsRestClient;
import demo.webauthn.grs.dto.RevocationWDash;
import demo.webauthn.grs.dto.RevocationWc;
import demo.webauthn.grs.poller.GrsApiPoller;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import java.io.IOException;
import java.util.List;

/** Standalone Java application launcher that runs the demo application. */
@Slf4j
public class EmbeddedServer {

  public static void main(String[] args) throws Exception {
    final int port = Config.getPort();

    WebAuthnServer webAuthnServer = new WebAuthnServer();
    InMemoryRegistrationStorage userStorage = webAuthnServer.getUserStorage();

    GrsRestClient client = new GrsRestClient("http://localhost:8085");
    GrsApiPoller poller = new GrsApiPoller(() -> {
      try {
        List<RevocationWc> allCredentialRegistrationsWC = userStorage.getAllCredentialRegistrationsWC();
        List<RevocationWDash> keys = client.getKeys(allCredentialRegistrationsWC);
        log.debug("Grs API Response: {}", keys);
        if (!keys.isEmpty()) {
          userStorage.checkAndRevokeKeys(keys);
        } else {
          log.debug("No Revocation keys found");
        }
      } catch (IOException e) {
        log.debug("Failed to call external API: {}", e.getMessage());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    poller.start(30);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.debug("Shutting down pollers...");
      poller.close();
    }));

    App app = new App(webAuthnServer);

    ResourceConfig config = new ResourceConfig();
    config.registerClasses(app.getClasses());
    config.registerInstances(app.getSingletons());

    SslContextFactory ssl = new SslContextFactory("keystore.jks");
    ssl.setKeyStorePassword("changeme"); //TODO: use better password

    Server server = new Server();
    HttpConfiguration httpConfig = new HttpConfiguration();
    httpConfig.setSecureScheme("https");
    httpConfig.setSecurePort(port);
    HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
    httpsConfig.addCustomizer(new SecureRequestCustomizer());

    ServerConnector connector =
        new ServerConnector(
            server,
            new SslConnectionFactory(ssl, HttpVersion.HTTP_1_1.asString()),
            new HttpConnectionFactory(httpsConfig));

    connector.setPort(port);
    connector.setHost("127.0.0.1");

    ServletHolder servlet = new ServletHolder(new ServletContainer(config));
    ServletContextHandler context = new ServletContextHandler(server, "/");
    context.addServlet(DefaultServlet.class, "/");
    context.setResourceBase("src/main/webapp");
    context.addServlet(servlet, "/api/*");

    server.setConnectors(new Connector[] {connector});
    try {
      server.start();
      log.info("Server started on port {}", Config.getPort());
      server.join();
    } finally {
      poller.close();
    }
  }
}
