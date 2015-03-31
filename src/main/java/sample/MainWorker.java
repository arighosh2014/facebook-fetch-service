package sample;

import io.vertx.java.redis.RedisClient;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.file.FileProps;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public abstract class MainWorker extends Verticle{
	protected String accessToken;
	private JsonObject requestParams;
	private JsonObject searchCredentials;
	protected HttpClient httpclient;
	protected HttpClient searchHttpClient;
	protected String searchApiAccessToken;
	private EventBus eb;
	private static RedisClient redis;
	private Handler<Message<JsonObject>> getCallingDetailsHandler;
	public void start() {
		eb = vertx.eventBus();
		
		if(requireCallingDetails()){
			getCallingDetailsHandler = new Handler<Message<JsonObject>>() {
			    public void handle(Message<JsonObject> message) {
			    	accessToken = message.body().getString("accessToken");
			    	requestParams = message.body().getObject("requestParams");
			    	searchCredentials = message.body().getObject("searchCredentials");
			    	unregisterHandler();
			    	getSearchAccessTokenThenFetch();
			    }
			};
			
			eb.registerHandler("sample.workernoticeboard", getCallingDetailsHandler);
		}else{
			doFetch();
		}
	}
	
	private void unregisterHandler(){
		eb.unregisterHandler("sample.workernoticeboard", getCallingDetailsHandler);
	}
	
	public RedisClient connectToRedis(){
		if(redis==null){
			redis = new RedisClient(vertx.eventBus(), "my.redis.address");
			redis.deployModule(container);
		}
		return redis;
	}
	
	private void getSearchAccessTokenThenFetch(){
		httpclient = vertx.createHttpClient().setSSL(true).setTrustAll(true).setHost("graph.facebook.com").setPort(443);
		searchHttpClient = vertx.createHttpClient().setHost("cloudsearch").setPort(8080);
		
		final HttpClientRequest request = searchHttpClient.post("/oauth/token", new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		            	JsonObject obj = new JsonObject(body.toString());
		            	searchApiAccessToken = "Bearer "+obj.getString("accessToken");
		            	doFetch();
		            }
		        });
			}
		});
		request.putHeader("Content-Length", Integer.toString(searchCredentials.toString().length()));
		request.putHeader("Content-Type", "application/json");
		request.setChunked(true);
        request.write(searchCredentials.toString());
        request.end();
	}
	
	private void doFetch(){
		connectToRedis();
		fetch();
		
	}
	
	protected abstract void fetch();
	
	protected boolean requireCallingDetails(){
		return true;
	}
	
	protected JsonObject getRequestParams(){
		return requestParams!=null?requestParams:new JsonObject();
	}

	public void log(String message){
		container.logger().info(message);
	}
	
	protected void appendToFile(final String file, final Buffer dataTobeWritten){
		vertx.fileSystem().props(file, new Handler<AsyncResult<FileProps>>(){
    		public void handle(AsyncResult<FileProps> asyncFile){
    			if(asyncFile.succeeded()){
    				final FileProps props = asyncFile.result();
    				vertx.fileSystem().open(file, new Handler<AsyncResult<AsyncFile>>(){
	        			public void handle(AsyncResult<AsyncFile> asyncFile){
	        				if (asyncFile.succeeded()) {
	        					AsyncFile file = asyncFile.result();
	        					file.write(dataTobeWritten,props.size(), new Handler<AsyncResult<Void>>(){
	    	            			public void handle(AsyncResult<Void> asyncResult){
	    	            				log("file is written....");
	    	            			}
	    	            		});
	        		        } else {
	        		            log("Failed to open file ::: "+ asyncFile.cause().getMessage());
	        		        }
	        			}
	        		});
    			}
    		}
    	});
	}
	
	protected void truncateFile(final String file){
		vertx.fileSystem().props(file, new Handler<AsyncResult<FileProps>>(){
    		public void handle(AsyncResult<FileProps> asyncFile){
    			if(asyncFile.succeeded()){
    				final FileProps props = asyncFile.result();
    				vertx.fileSystem().delete(file, new Handler<AsyncResult<Void>>(){
    		    		public void handle(AsyncResult<Void> asyncFile){
    		    			log("File is deleted......");
    		    			vertx.fileSystem().createFile(file, new Handler<AsyncResult<Void>>(){
    	    		    		public void handle(AsyncResult<Void> asyncFile){
    	    		    			log("File is created......");
    	    		    		}
    	    		    	});
    		    		}
    		    	});
    			}
    		}
    	});
	}

}
