package sample;
/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */



import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;

/*
This is a simple Java verticle which fetches facebook page posts.
 */
public class FacebookFetchVerticle extends MainVerticle {

	public void doStart() {
		
		RouteMatcher routeMatcher = new RouteMatcher();

		routeMatcher.get("/fetchPageDetails", new Handler<HttpServerRequest>() {
		    
			public void handle(final HttpServerRequest req) {
				req.response().headers().add("Access-Control-Allow-Origin", "*");
				JsonObject params = new JsonObject();
				params.putString("query", req.params().get("query"));
				params.putString("type", req.params().get("type"));
				JsonObject appConfig = container.config();
				List<Map<String,Object>> modules = new ArrayList<Map<String,Object>>(); 
		    	modules.add(createADeployableModule("sample.FacebookFetch", new Handler<Message<JsonObject>>() {
				    public void handle(Message<JsonObject> message) {
				    	log("I received a message " + message.body().getString("response"));
				        //req.response().end(message.body().getString("response"));
				    }
				}));
		    	
		    	modules.add(createADeployableModule("sample.FetchPagePosts", new Handler<Message<JsonObject>>() {
				    public void handle(Message<JsonObject> message) {
				    	//log("** I received a message " + message.body().getString("response"));
				        req.response().end("found :: "+message.body().getString("response")+" page. Storing the posts of this page..");
				    }
				}));				    	
		    	deploySecuredModules(params,accessToken,searchCredentials,modules);
				
		    }
		});
		routeMatcher.get("/welcome", new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest req) {
				req.response().headers().add("Access-Control-Allow-Origin", "*");
				String query = req.params().get("query");
				log(query);
		    	req.response().end("welcome to facebook fetch service..");
		    }
		});
		
		routeMatcher.get("/searchPosts", new Handler<HttpServerRequest>() {
			public void handle(final HttpServerRequest req) {
				JsonObject params = new JsonObject();
				params.putString("query", req.params().get("query"));
				params.putString("type", req.params().get("type"));
				List<Map<String,Object>> modules = new ArrayList<Map<String,Object>>(); 
		    	modules.add(createADeployableModule("sample.SearchPosts", new Handler<Message<JsonObject>>() {
				    public void handle(Message<JsonObject> message) {
				    	req.response().headers().add("Access-Control-Allow-Origin", "*");
				    	req.response().end(message.body().getArray("response").toString());
				    }
				}));
		    	deploySecuredModules(params,accessToken,searchCredentials,modules);
		    }
		});
		
		 
		//vertx.createHttpServer().requestHandler(routeMatcher).listen(8888);
		vertx.createHttpServer().requestHandler(routeMatcher).setSSL(true).setKeyStorePath("server.jks").setKeyStorePassword("password").listen(4443);
		log("Webserver started, Listening on port: 4443");
	}
}