package sample;

import io.vertx.java.redis.RedisClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;


public class FetchPagePostsWorker extends MainWorker{

	@Override
	protected void fetch(){
		String query = getRequestParams().getString("query");
		String type = getRequestParams().getString("type")!=null?getRequestParams().getString("type"):"page";
		String encoadedQuery=null;
		try {
			encoadedQuery = URLEncoder.encode(query,"UTF-8").replaceAll(Pattern.quote("+"), "%20");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		String queryString = "q="+encoadedQuery+"&type="+type;

		String fields = "&fields=category,name,id";
		//truncateFile("/home/arighosh/result.txt");
		
		httpclient.getNow("/v2.2/search?"+queryString+fields+"&access_token="+accessToken, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		               // The entire response body has been received
		            	callPswSearch("POST","/customers/facebookFetch", null,null, "text/plain;charset=UTF-8","going to create organization...");
		            	int i = findAllPages(body.toString(), 0);
		            	log("**************************************&&&&&&&&&&&&&********************************************************");
		            	JsonObject respToBrowser = new JsonObject();
		            	respToBrowser.putString("response",new Integer(i).toString());
		            	vertx.eventBus().publish("sample.fetchpageposts", respToBrowser);
		            }
		        });
			}
		});
	}
	
	private int findAllPages(String response, int numberOfPagesCounter){
		int i = fetchCurrentPages(response, numberOfPagesCounter);
		numberOfPagesCounter = i;
    	String nextPageLink = Helper.findNextPageLink(response);
    	if(nextPageLink!=null && !"".equals(nextPageLink)){
    		findNextPage(nextPageLink, numberOfPagesCounter);
    	}
    	return numberOfPagesCounter;
	}
	
	
	private int fetchCurrentPages(String pagesStr, int numberOfPagesCounter){
		JsonObject dataObj = new JsonObject(pagesStr);
		JsonArray array = dataObj.getArray("data");
		if(array!=null){
			Iterator<Object> iter = array.iterator();
			while(iter.hasNext()){
				numberOfPagesCounter++;
				JsonObject pageObj = (JsonObject)iter.next();
				String id = pageObj.getString("id");
				String name = pageObj.getString("name");
				RedisClient client = connectToRedis();
				client.set(id,name, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> arg0) {
						log("wrote index name into redis..");
					}	   
				});
				callPswSearch("POST","/customers/facebookFetch/indexes/"+id, null,null, "text/plain;charset=UTF-8","going to create index...");
				findAPagePost(id, numberOfPagesCounter, name);
				log("total number of pages found :"+numberOfPagesCounter);
			}
		}
		return numberOfPagesCounter;
	}
	
	private void findNextPage(String nextPageLink, final int numberOfPagesCounter){
		httpclient.getNow(nextPageLink, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		            	findAllPages(body.toString(), numberOfPagesCounter);
		            }
		        });
			}
		});
	}
	
	private void findAPagePost(final String id, final int pageNumber, final String name){
		httpclient.getNow("/v2.2/"+id+"/posts?fields=message&access_token="+accessToken, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		            	
		            	JsonObject posts = new JsonObject(body.toString());
		            	String postsStr = posts.getArray("data").toString();
		            	final Buffer dataTobeWritten = new Buffer(postsStr);
		            	//appendToFile("/home/arighosh/result.txt", dataTobeWritten);
		            	sendPostMessagesToPswSearch(posts.getArray("data"), name,id);
		            	Counter numberOfPostPagesCounter = new Counter();
		            	numberOfPostPagesCounter.setCount(1);
		            	findSubsequentPagesPosts(body.toString(),numberOfPostPagesCounter,pageNumber,name,id);
		            	
		            }
		        });
			}
		});
	}
	
	
	
	private void findSubsequentPagesPosts(String response, Counter numberOfPostPages, int pageNumber, String name, String pageId){
		String nextPagePostsLink = Helper.findNextPageLink(response);
    	if(nextPagePostsLink!=null && !"".equals(nextPagePostsLink)){
    		findNextPagePosts(nextPagePostsLink, numberOfPostPages,pageNumber,name, pageId);
    	}
	}
	
	private void findNextPagePosts(String nextPageLink, final Counter numberOfPostPagesCounter, final int pageNumber, final String name, final String pageId){
		httpclient.getNow(nextPageLink, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		            	JsonObject posts = new JsonObject(body.toString());
		            	if(posts.getArray("data")!=null){
		            		String postsStr = posts.getArray("data").toString();
			            	final Buffer dataTobeWritten = new Buffer(postsStr);
			            	//appendToFile("/home/arighosh/result.txt", dataTobeWritten);
			            	sendPostMessagesToPswSearch(posts.getArray("data"), name, pageId);
			            	numberOfPostPagesCounter.setCount(numberOfPostPagesCounter.getCount()+1);
			            	log("=============Total post pages found for page no ::"+ pageNumber+" ::"+numberOfPostPagesCounter.getCount());
			            	findSubsequentPagesPosts(body.toString(),numberOfPostPagesCounter, pageNumber,name,pageId);
		            	}
		            }
		        });
			}
		});
	}
	
	private void sendPostMessagesToPswSearch(JsonArray postsArray, String pageName, String pageId){
		if(postsArray!=null){
			Iterator<Object> iter = postsArray.iterator();
			while(iter.hasNext()){
				JsonObject postsObj = (JsonObject)iter.next();
				String message = postsObj.getString("message");
				String id = postsObj.getString("id");
				JsonObject metadata = new JsonObject();
				metadata.putString("created_time", postsObj.getString("created_time"));
				id = id.replace("_", "dash");
				if(message!=null && !"".equals(message)){
					callPswSearch("PUT","/customers/facebookFetch/indexes/"+pageId+"/contents/"+id, message,metadata, "application/json","going to add content...");
				}
			}
		}
	}
	
	private void callPswSearch(String method,final String uri, String content,JsonObject metadata ,String contentType, final String logMessage){
		content = StringEscapeUtils.escapeJava(content);
		final String logContent = content;
		final HttpClientRequest request = searchHttpClient.request(method,uri, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				if(resp.statusCode()==400 || resp.statusCode()==500){
					log(logContent);
					log("Status code ::: "+resp.statusCode()+", Status Message ::: "+resp.statusMessage());
				}
				final Buffer body = new Buffer(0);
				Helper.readResponseBody(resp, body);
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		            	log(body.toString());
		            }
		        });
			}
		});
		request.putHeader("Authorization", searchApiAccessToken);
    	request.putHeader("Content-Type", contentType);
		if(content!=null && !"".equals(content)){
			request.putHeader("Content-Length", Integer.toString(content.length()));
			request.write(content);
		}else{
			request.putHeader("Content-Length", "0");
		}
		if(metadata!=null && !"".equals(metadata.toString())){
			request.putHeader("metadata", metadata.toString());
		}
    	request.end();
	}
	
}
