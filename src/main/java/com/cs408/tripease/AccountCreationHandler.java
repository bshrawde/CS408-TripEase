package com.cs408.tripease;


import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.FormLoginHandler;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.lang.String;
import java.util.Random;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import io.vertx.ext.auth.jdbc.impl.JDBCAuthImpl;

public class AccountCreationHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(AccountCreationHandler.class);


    private String usernameParam = "username";
    private String passwordParam = "password";
    private String passwordConfirmParam = "passwordConfirm";
    private String emailParam = "email";
    private String emailConfirmParam = "emailConfirm";
    private String redirectURL = "/login";

    private JDBCClient jdbcClient;

    public static AccountCreationHandler create(JDBCClient jc) {
        AccountCreationHandler ach = new AccountCreationHandler();
        ach.jdbcClient = jc;
        return ach;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest req = context.request();
        if (req.method() != HttpMethod.POST) {
            context.fail(405); // Must be a POST
        } else {
            if (!req.isExpectMultipart()) {
                throw new IllegalStateException("Form body not parsed - do you forget to include a BodyHandler?");
            }
            MultiMap params = req.formAttributes();
            String username = params.get(usernameParam);
            String password = params.get(passwordParam);
            String passwordConfirm = params.get(passwordConfirmParam);
            String email = params.get(emailParam);
            String emailConfirm = params.get(emailConfirmParam);
            if (username == null || password == null || passwordConfirm == null ||
                    email == null || emailConfirm == null) {
                log.warn("Improper parameters inputted.");
                context.fail(400);
            } else {

                //Add error checking for params here
                
                String salt = getSalt();
                String hexPassword = JDBCAuthImpl.computeHash(password, salt, "SHA-512");
    

                if(!password.equals(passwordConfirm)){
					//passwords do not match print errror
					log.warn("Password does not match\n\n\n");
					context.fail(400);
				}
				if(!email.equals(emailConfirm)){
					//emails do not match print error
					log.warn("Emails do not match\n\n\n");
					context.fail(400);
				}
				Pattern P = Pattern.compile("[a-z0-9].+@.+\\.[a-z]+");
				Matcher m = P.matcher(email);
				boolean matchFound  = m.matches();

				if(!matchFound){
					log.warn("not a vaild email\n\n\n");
					context.fail(400);
				}
                jdbcClient.getConnection(res -> {
                    if (res.succeeded()) {
                        SQLConnection connection = res.result();
                        
                        connection.execute("INSERT INTO user VALUES ('" + username + "', '" + hexPassword + "', '" + salt + "', '" + email + "')", res2 -> {
                            if (res2.succeeded()) {
                                doRedirect(req.response(), redirectURL);
                            } else {
                                log.error("Could not create the user account in the database.");
                            }

                        });
                    } else {
                        log.error("Could not connect to database.");
                        context.fail(402);
                    }
                });
            }
        }
    }
    
    private String getSalt() {
        final Random r = new SecureRandom();
		byte[] salt = new byte[32];
		r.nextBytes(salt);
        return toHex(salt);
    }
    
    
    private String toHex(byte[] b) {
		//Convert hash to hex string.
		char[] hexArray = "0123456789ABCDEF".toCharArray();
		char[] hexChars = new char[b.length * 2];
		for ( int j = 0; j < b.length; j++ ) {
			int v = b[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
    

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader("location", url).setStatusCode(302).end();
    }
}
