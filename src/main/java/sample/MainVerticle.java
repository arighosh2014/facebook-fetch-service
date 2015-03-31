package sample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;


public abstract class MainVerticle extends Verticle{
	protected String accessToken;
	protected JsonObject searchCredentials;
		
	public void start() {
		
		container.deployWorkerVerticle("sample.AccessTokenWorker",null,1,false, new Handler<AsyncResult<String>>() {
		    public void handle(AsyncResult<String> asyncResult) {
		        if (asyncResult.succeeded()) {
		            System.out.println("AccessTokenWorker has been deployed, deployment ID is " + asyncResult.result());
		        }
		    }
		});
		
		Handler<Message<JsonObject>> accessTokenFetchHandler = new Handler<Message<JsonObject>>() {
		    public void handle(Message<JsonObject> message) {
		    	accessToken = message.body().getString("accessToken");
		    	searchCredentials = message.body().getObject("searchCredentials");
		    	doStart();
		    }
		};
		
		vertx.eventBus().registerHandler("sample.accesstokenfetch", accessTokenFetchHandler);
	}
	
	protected abstract void doStart();
	
	protected Map<String,Object> createADeployableModule(String modulename, Handler<Message<JsonObject>> handler){
		Map<String,Object> aModule = new HashMap<String,Object>();
		aModule.put("module_name", modulename);
    	aModule.put("handler", handler);
    	return aModule;
	}
	
	protected void deploySecuredModules(final JsonObject params,final String accessToken, final JsonObject searchCredentials,List<Map<String,Object>> modules){
		final JsonObject requestDetails = new JsonObject();
		requestDetails.putString("accessToken", accessToken);
		requestDetails.putObject("requestParams", params);
		requestDetails.putObject("searchCredentials", searchCredentials);
		
		for (Map<String, Object> map : modules) {
			String moduleName = (String) map.get("module_name");
			Handler<Message<JsonObject>> handler = (Handler<Message<JsonObject>>) map.get("handler");
			final String workerName = moduleName+"Worker";
			container.deployWorkerVerticle(workerName,null,1,false, new Handler<AsyncResult<String>>() {
			    public void handle(AsyncResult<String> asyncResult) {
			        if (asyncResult.succeeded()) {
			            System.out.println(workerName+" has been deployed, deployment ID is " + asyncResult.result());
			            vertx.eventBus().publish("sample.workernoticeboard", requestDetails);
			        }
			    }
			});
			
			vertx.eventBus().registerHandler(moduleName.toLowerCase(), handler);
			
		}
	}
	public void log(String message){
		container.logger().info(message);
	}

}
