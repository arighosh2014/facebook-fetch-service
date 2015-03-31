package sample;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;

public class Helper {

	public static String findNextPageLink(String response){
		//find the next pages
    	JsonObject dataObj = new JsonObject(response);
		JsonObject nextPage = dataObj.getObject("paging");
		String nextPageLink = nextPage!=null?nextPage.getString("next"):null;
		if(nextPageLink!=null && !"".equals(nextPageLink)){
			nextPageLink = nextPageLink.substring("https://graph.facebook.com".length());
		}
		return nextPageLink;
	}
	
	public static void readResponseBody(HttpClientResponse resp, final Buffer body){
		resp.dataHandler(new Handler<Buffer>() {
            public void handle(Buffer data) {
                body.appendBuffer(data);
            }
        });
	}

}
