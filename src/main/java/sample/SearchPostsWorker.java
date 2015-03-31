package sample;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class SearchPostsWorker extends MainWorker{

	@Override
	protected void fetch(){
		String query = getRequestParams().getString("query");
		String encoadedQuery=null;
		try {
			encoadedQuery = URLEncoder.encode(query,"UTF-8").replaceAll(Pattern.quote("+"), "%20");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		final HttpClientRequest request = searchHttpClient.request("GET","/customers/facebookFetch/indexes/*/contents?query="+encoadedQuery, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		            	final JsonObject result = new JsonObject();
		            	log(body.toString());
		            	JsonObject response = new JsonObject(body.toString());
		            	final JsonArray indexArray = response.getArray("indexes");
		            	final JsonArray indexResponseArray = new JsonArray();
		            	final Counter counter = new Counter();
		            	counter.setCount(0);
		            	for (Object object : indexArray) {
		            		final JsonObject indexResponseObj = new JsonObject();
		            		final JsonArray contentsArray = ((JsonObject)object).getArray("contents");
		            		String IndexId = ((JsonObject)object).getString("indexId");
		            		connectToRedis().get(IndexId,new Handler<Message<JsonObject>>() {
		    					@Override
		    					public void handle(Message<JsonObject> message) {
		    						indexResponseObj.putString("page_name", message.body().getString("value"));
		    						JsonArray contentResponseArray = null;
				            		if(contentsArray!=null && contentsArray.size()>0){
				            			contentResponseArray = new JsonArray();
					            		for (Object content : contentsArray) {
					            			String postMessage = ((JsonObject)content).getString("contentSummary");
					            			JsonObject contentResponse = new JsonObject();
					            			contentResponse.putString("content", StringEscapeUtils.unescapeJava(postMessage));
					            			contentResponse.putArray("additional_info", ((JsonObject)content).getArray("metadata"));
					            			contentResponseArray.add(contentResponse);
										}
				            			indexResponseObj.putArray("contents", contentResponseArray);
				            			indexResponseArray.add(indexResponseObj);
				            		}
				            		counter.setCount(counter.getCount()+1);
				            		if(counter.getCount()==indexArray.size()){
				            			result.putArray("response", indexResponseArray);
						            	vertx.eventBus().publish("sample.searchposts", result);
				            		}
		    					}	   
		    				});
		            		
						}
		            	
		            }
		        });
			}
		});
		request.putHeader("Authorization", searchApiAccessToken);
		request.end();
	}

}
