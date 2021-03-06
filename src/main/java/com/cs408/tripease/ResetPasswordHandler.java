package com.cs408.tripease;


import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.json.JsonArray;
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

public class ResetPasswordHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(ResetPasswordHandler.class);


    private String usernameParam = "username";
    private String passwordParam = "NewPass";
    private String passwordConfirmParam = "NewPassConfirm";
    private String emailParam = "email";
    private String secretAnswerParam = "secretAnswer";
    private String redirectURL = "/login";
    String checker = "";

    private JDBCClient jdbcClient;

    public static ResetPasswordHandler create(JDBCClient jc) {
        ResetPasswordHandler ach = new ResetPasswordHandler();
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
            String secret = params.get(secretAnswerParam);

            if (username == null || password == null || passwordConfirm == null ||
                    email == null || secret==null) {
                log.warn("Improper parameters inputted in rester handler.");
                context.fail(400);
            } else {

                //Add error checking for params here
                
                String salt = getSalt();
                String hexPassword = JDBCAuthImpl.computeHash(username, salt, "SHA-512");

                if(username.equals("")) {
                    log.warn("Username is not valid");
                    context.session().put("errorCreateAccount", "Username is not valid.");
                    doRedirect(req.response(), "create");
                    return;
                }

                if(username.length() > 50 || username.length() < 1) {
                    log.warn("Username is a invalid length");
                    context.session().put("errorCreateAccount", "Username is too long.");
                    doRedirect(req.response(), "create");
                    return;
                }

                if(password.length() > 256) {
                    log.warn("Password is a invalid length");
                    context.session().put("errorCreateAccount", "Password is too long. Please shorten.");
                    doRedirect(req.response(), "create");
                    return;
                }
                if(secret.contains("[^a-zA-Z' ']")){
                    log.warn("not a valid entry");
                    context.session().put("errorCreateAccount", "Security question is not valid.");
                    doRedirect(req.response(),"create");
                }

                if(!password.equals(passwordConfirm)){
					//passwords do not match print errror
					log.warn("Password does not match");
                    context.session().put("errorCreateAccount", "Passwords does not match.");
                    doRedirect(req.response(), "create");
                    return;
				}
                Pattern P = Pattern.compile("[a-z0-9].+@.+\\.[a-z]+");
                Matcher m = P.matcher(email);
                boolean matchFound = m.matches();

                if (!matchFound) {
                    log.warn("not a vaild email");
                    context.session().put("errorCreateAccount", "Email is not a valid email.");
                    doRedirect(req.response(), "create");
                    return;
                }
                jdbcClient.getConnection(res -> {
                    if (res.succeeded()) {
                        SQLConnection connection = res.result();

                        connection.query("SELECT Answer FROM user WHERE username = '"+username+"'", res3 -> {
                        if(res.succeeded()){
                            for(JsonArray line : res3.result().getResults()){
                                checker = line.encode();
                                checker = checker.replaceAll("[^a-zA-Z]","");
                            }
                            if(checker.equals(secret)){
                                String update = "UPDATE user SET password = '" +hexPassword+"', password_salt = '"+salt+"'";
                                    connection.update(update, res2 -> {
                                            if (res2.succeeded()) {
                                                doRedirect(req.response(), redirectURL);
                                                context.session().remove("errorCreateAccount");
                                                return;
                                            } else {
                                                log.error("Could not create the user account in the database.");
                                                context.session().put("errorCreateAccount", "Username is already in use.");
                                                doRedirect(req.response(), "create");
                                                return;
                                            }

                                    });
                            }else{
                                log.error("Incorrect Answer to question check spelling and caps");
                                context.session().put("errorResetPass", "Incorrect Answer to question check spelling");
                                doRedirect(req.response(), "ForgotPassword");
                                return;
                            }
                        }
                        });
                    } else {
                        log.error("Could not connect to database.");
                        context.session().put("errorCreateAccount", "Could not connect to the database.");
                        doRedirect(req.response(), "create");
                        return;
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
