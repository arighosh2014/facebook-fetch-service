package sample;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;

public class FacebookFetchWorker extends MainWorker{
	
	@Override
	protected void fetch() {
		httpclient.getNow("/v2.2/me?access_token="+accessToken, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				log("call is successful"+ resp.statusCode());
				final Buffer body = new Buffer(0);

		        resp.dataHandler(new Handler<Buffer>() {
		            public void handle(Buffer data) {
		                body.appendBuffer(data);
		            }
		        });
		        
		        resp.endHandler(new Handler<Void>() {
		            public void handle(Void arg0) {
		               // The entire response body has been received
		            	log("The total body received was " + body.length() + " bytes");
		            	JsonObject obj = new JsonObject();
		            	obj.putString("response", body.toString());
		            	vertx.eventBus().publish("sample.fecebookfetch", obj);
		            }
		        });
			}
		});
	}

}
