/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.text.TextUtils;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.net.Proxy;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.NonNull;
import okio.Buffer;
import okio.ByteString;

public class BasicDigestAuthenticator implements Authenticator {
    protected static final String
            HEADER_AUTHENTICATE = "WWW-Authenticate",
            HEADER_AUTHORIZATION = "Authorization";

    final String host, username, password;

    final String clientNonce;
    AtomicInteger nonceCount = new AtomicInteger(1);


    public BasicDigestAuthenticator(String host, String username, String password) {
        this.host = host;
        this.username = username;
        this.password = password;

        clientNonce = h(UUID.randomUUID().toString());
    }

    BasicDigestAuthenticator(String host, String username, String password, String clientNonce) {
        this.host = null;
        this.username = username;
        this.password = password;
        this.clientNonce = clientNonce;
    }


    @Override
    public Request authenticate(Proxy proxy, Response response) throws IOException {
        Request request = response.request();

        if (host != null && !request.httpUrl().host().equalsIgnoreCase(host)) {
            Constants.log.warn("Not authenticating against " +  host + " for security reasons!");
            return null;
        }

        // check whether this is the first authentication try with our credentials
        Response priorResponse = response.priorResponse();
        boolean triedBefore = priorResponse != null ? priorResponse.request().header(HEADER_AUTHORIZATION) != null : false;

        HttpUtils.AuthScheme basicAuth = null, digestAuth = null;
        for (HttpUtils.AuthScheme scheme : HttpUtils.parseWwwAuthenticate(response.headers(HEADER_AUTHENTICATE).toArray(new String[0])))
            if ("Basic".equals(scheme.name))
                basicAuth = scheme;
            else if ("Digest".equals(scheme.name))
                digestAuth = scheme;

        // we MUST prefer Digest auth [https://tools.ietf.org/html/rfc2617#section-4.6]
        if (digestAuth != null) {
            // Digest auth

            if (triedBefore && !"true".equalsIgnoreCase(digestAuth.params.get("stale")))
                // credentials didn't work last time, and they won't work now -> stop here
                return null;
            return authorizationRequest(request, digestAuth);

        } else if (basicAuth != null) {
            // Basic auth
            if (triedBefore)    // credentials didn't work last time, and they won't work now -> stop here
                return null;
            return request.newBuilder()
                    .header(HEADER_AUTHORIZATION, Credentials.basic(username, password))
                    .build();
        }

        // no supported auth scheme
        return null;
    }

    @Override
    public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
        return null;
    }


    protected Request authorizationRequest(Request request, HttpUtils.AuthScheme digest) {
        String  realm = digest.params.get("realm"),
                opaque = digest.params.get("opaque"),
                nonce = digest.params.get("nonce");

        Algorithm algorithm = Algorithm.determine(digest.params.get("algorithm"));
        Protection qop = Protection.selectFrom(digest.params.get("qop"));

        // build response parameters
        String response = null;

        List<String> params = new LinkedList<>();
        params.add("username=" + quotedString(username));
        if (realm != null)
            params.add("realm=" + quotedString(realm));
        else
            return null;
        if (nonce != null)
            params.add("nonce=" + quotedString(nonce));
        else
            return null;
        if (opaque != null)
            params.add("opaque=" + quotedString(opaque));
        else
            return null;

        final String method = request.method();
        final String digestURI = request.httpUrl().encodedPath();
        params.add("uri=" + quotedString(digestURI));

        if (qop != null) {
            params.add("qop=" + qop.name);

            params.add("cnonce=" + quotedString(clientNonce));

            int nc = nonceCount.getAndIncrement();
            String ncValue = String.format("%08x", nc);
            params.add("nc=" + ncValue);

            String a1 = null;
            if (algorithm == Algorithm.MD5)
                a1 = username + ":" + realm + ":" + password;
            else if (algorithm == Algorithm.MD5_SESSION)
                a1 = h(username + ":" + realm + ":" + password) + ":" + nonce + ":" + clientNonce;
            //Constants.log.trace("A1=" + a1);

            String a2 = null;
            if (qop == Protection.Auth)
                a2 = method + ":" + digestURI;
            else if (qop == Protection.AuthInt)
                try {
                    RequestBody body = request.body();
                    a2 = method + ":" + digestURI + ":" + (body != null ? h(body) : h(""));
                } catch(IOException e) {
                    Constants.log.warn("Couldn't get entity-body for hash calculation");
                }
            //Constants.log.trace("A2=" + a2);

            if (a1 != null && a2 != null)
                response = kd(h(a1), nonce + ":" + ncValue + ":" + clientNonce + ":" + qop.name + ":" + h(a2));

        } else {
            // legacy (backwards compatibility with RFC 2069)
            if (algorithm == Algorithm.MD5) {
                String  a1 = username + ":" + realm + ":" + password,
                        a2 = method + ":" + digestURI;
                response = kd(h(a1), nonce + ":" + h(a2));
            }
        }

        if (response != null) {
            params.add("response=" + quotedString(response));
            return request.newBuilder()
                    .header(HEADER_AUTHORIZATION, "Digest " + TextUtils.join(", ", params))
                    .build();
        } else
            return null;
    }

    protected String quotedString(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    protected String h(String data) {
        return ByteString.of(data.getBytes()).md5().hex();
    }

    protected String h(@NonNull RequestBody body) throws IOException {
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        return ByteString.of(buffer.readByteArray()).md5().hex();
    }

    protected String kd(String secret, String data) {
        return h(secret + ":" + data);
    }


    protected enum Algorithm {
        MD5("MD5"),
        MD5_SESSION("MD5-sess");

        public final String name;
        Algorithm(String name) { this.name = name; }

        static Algorithm determine(String paramValue) {
            if (paramValue == null || Algorithm.MD5.name.equals(paramValue))
                return Algorithm.MD5;
            else if (Algorithm.MD5_SESSION.name.equals(paramValue))
                return Algorithm.MD5_SESSION;
            else
                Constants.log.warn("Ignoring unknown hash algorithm: " + paramValue);
                return null;
        }
    }

    protected enum Protection {    // quality of protection:
        Auth("auth"),              // authentication only
        AuthInt("auth-int");       // authentication with integrity protection

        public final String name;
        Protection(String name) { this.name = name; }

        static Protection selectFrom(String paramValue) {
            if (paramValue != null) {
                boolean qopAuth = false,
                        qopAuthInt = false;
                for (String qop : paramValue.split(","))
                    if ("auth".equals(qop))
                        qopAuth = true;
                    else if ("auth-int".equals(qop))
                        qopAuthInt = true;

                // prefer auth
                if (qopAuth)
                    return Auth;
                else if (qopAuthInt)
                    return AuthInt;
            }
            return null;
        }
    }

}