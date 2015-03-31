package sample;

import org.vertx.java.core.json.JsonObject;

public class AccessTokenWorker extends MainWorker{

	@Override
	protected void fetch() {
		String appId = "1551527318436462";
		String appSecrect = "499aa9056fde961c8c76f1893e648607";
		String searchClientId = "or4NZb8QjETuLAId7fvVZg";
		String searchClientSecrect = "5YU59C5bbuMvO8LL77WsBg";
		JsonObject obj = new JsonObject();
    	JsonObject searchCredential = new JsonObject();
    	searchCredential.putString("clientId", searchClientId);
    	searchCredential.putString("clientSecret", searchClientSecrect);
    	
    	obj.putString("accessToken", appId+"|"+appSecrect);
    	obj.putObject("searchCredentials", searchCredential);
    	vertx.eventBus().publish("sample.accesstokenfetch", obj);
	}
	
	protected boolean requireCallingDetails(){
		return false;
	}

}
