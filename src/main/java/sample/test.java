package sample;

import org.vertx.java.platform.Verticle;

public abstract class test extends Verticle{
	
	public void start() {
		doStart();
	}
	
	protected abstract void doStart();

}
