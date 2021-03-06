package com.cs408.tripease;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.net.JksOptions;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;

import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.*;
import io.vertx.ext.auth.jdbc.impl.JDBCAuthImpl;

import io.vertx.ext.web.templ.MVELTemplateEngine;


public class TripEaseServer extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(TripEaseServer.class);

    private static int port;
    private static String hostname;

    private static JsonObject jdbcConfig = new JsonObject();

    @Override
    public void start() {
        Vertx vertx = Vertx.vertx();

        HttpServerOptions options = new HttpServerOptions().setSsl(true).setKeyStoreOptions(
            new JksOptions().
                setPath("webroot/keystore.jks").
                setPassword("cs40800")
        );


        HttpServer server = Vertx.vertx().createHttpServer(options);
        Router router = Router.router(vertx);

        //JDBC Client
        JDBCClient jdbcClient = JDBCClient.createShared(vertx, jdbcConfig);
        JDBCAuth authProvider = JDBCAuth.create(jdbcClient);
		authProvider.setAuthenticationQuery("SELECT PASSWORD, PASSWORD_SALT FROM user WHERE USERNAME = ?");

        //Various handlers doing a variety of things.
        router.route().handler(BodyHandler.create());
        router.route().handler(CookieHandler.create());
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().handler(UserSessionHandler.create(authProvider));

        //Favicon Love
        router.route().handler(FaviconHandler.create("webroot/images/favicon.ico"));


        MVELTemplateEngine tEngine = MVELTemplateEngine.create();


        //Planner is our application area, so we need them to login before that.
        router.route("/userdetails*").handler(RedirectAuthHandler.create(authProvider, "/login"));
        router.route("/userpreferences*").handler(RedirectAuthHandler.create(authProvider, "/login"));
        router.route("/tripPossibilities*").handler(RedirectAuthHandler.create(authProvider, "/login"));

        //Our login form sends the login information via HTTP Post we handle that here
        //Goes to /planner if successful login, 403 if failure.
        router.post("/login").handler(
            LoginHandler.create(authProvider, "/userdetails")
        );

        //The actual login page, from this page the use submits the login information
        router.route("/login").handler(
            FileTemplateHandler.create(tEngine, "webroot/login.templ")
        );

        router.route("/logoff").handler(routingContext -> {
            routingContext.clearUser();
            routingContext.response().putHeader("location", "/").setStatusCode(302).end();
        });

        router.post("/create").handler(AccountCreationHandler.create(jdbcClient));
        router.route("/create").handler(
                FileTemplateHandler.create(tEngine, "webroot/create.templ")
        );

        router.post("/ForgotPassword").handler(ResetPasswordHandler.create(jdbcClient));
        router.route("/ForgotPassword").handler(
                FileTemplateHandler.create(tEngine, "webroot/ForgetPassword.templ")
        );
	    //The user details page
	    router.post("/userdetails").handler(AccountDetailsHandler.create(jdbcClient));
        router.route("/userdetails").handler(
                FileTemplateHandler.create(tEngine, "webroot/userdetails.templ")
        );
	    //The usser preferences page
	    router.post("/userpreferences").handler(AccountPreferencesHandler.create(jdbcClient));
        router.route("/userpreferences").handler(
                FileTemplateHandler.create(tEngine, "webroot/preferences.templ")
        );
	    //The Trip possibilities page
	    router.route("/tripPossibilities").handler(
                EverythingIsPossibleHandler.create(jdbcClient)
        );
        router.route("/tripPossibilities").handler(
                FileTemplateHandler.create(tEngine, "webroot/possibilities.templ")
        );




        //Assets routing
        router.routeWithRegex(".+(fonts|css|images|js).*").handler(
            StaticHandler.create()
        );

        //Simple Pages
        router.route("/").handler(FileTemplateHandler.create(tEngine, "webroot/index.templ"));
        router.route("/about").handler(
                FileTemplateHandler.create(tEngine, "webroot/about.templ")
        );



        TripEaseServer.log.info("TripEase server started at port:" + port + " on " + hostname);
        server.requestHandler(router::accept).listen(port, hostname);
        
    }

    public static void main(String[] args) {
        try {
            port = Integer.parseInt(System.getenv("OPENSHIFT_VERTX_PORT"));
        } catch (NullPointerException | NumberFormatException ex) {
            TripEaseServer.log.info("Could not find Openshift port, using port 8080.");
            port = 8070;
        }

        try {
            hostname = System.getenv("OPENSHIFT_VERTX_IP");
            if (hostname == null) {
                throw new NullPointerException();
            }
        } catch (NullPointerException nullex) {
            TripEaseServer.log.info("Could not find Openshift hostname, using localhost.");
            hostname = "localhost";
        }

        int dbPort;
        String dbHostname, dbUsername, dbPassword;
        try {
            dbHostname = System.getenv("OPENSHIFT_MYSQL_DB_HOST");
            dbPort = Integer.parseInt(System.getenv("OPENSHIFT_MYSQL_DB_PORT"));
            dbUsername = System.getenv("OPENSHIFT_MYSQL_DB_USERNAME");
            dbPassword = System.getenv("OPENSHIFT_MYSQL_DB_PASSWORD");
        } catch (NullPointerException | NumberFormatException ex) {
            TripEaseServer.log.fatal("Could not get database information! Trying local.. ");

            dbPort = 3306;
            dbHostname = "localhost";
            dbUsername = "bshrawder";
            dbPassword = "X2TeN7NNX7XJdJsJ";
        }
        jdbcConfig.put("url", "jdbc:mysql://" + dbHostname + ":" + dbPort + "/tripease");
        jdbcConfig.put("user", dbUsername);
        jdbcConfig.put("password", dbPassword);
        jdbcConfig.put("driver_class", "com.mysql.jdbc.Driver");


        //Not sure if more preferred way to start.
        TripEaseServer server = new TripEaseServer();
        server.start();
    }
}
